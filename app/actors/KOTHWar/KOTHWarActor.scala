package actors.KOTHWar

import akka.actor.Actor
import akka.actor.Props
import play.api.Logger
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.libs.ws.WSResponse
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await
import play.api.libs.json._
import org.omg.CORBA.Object
import scala.concurrent.ExecutionContext.Implicits.global

class KOTHWarActor extends Actor{
	implicit val timeout = Timeout(5 seconds)
	//Staic Data
	var event_id:Int= 0
	var map_id:Int= 0
	var war_id:String = null
	var guild_ids:List[String] = null
  var duration:Int = 0
	var node_ids:List[Int] = null
	var koth_units:Map[Int,Int] = null // key unit_id value deploy_point
	var multiplier_threshold:Map[Int,Double] = null
	var is_war_over = false
	val guild_progress_goals: ArrayBuffer[String] = ArrayBuffer()
	//Dynamic Data
	val nodes:Map[Int,Int] = Map() //point deployed on the node

	val guilds_deploy:Map[String,(Int,Int)] = Map() //overall guild points
	val guil_points_per_node:ArrayBuffer[(Int,String,Int)] =  ArrayBuffer() //(node_id,guild_id,points)

	val player_deploy:Map[String,(Int,Int)]=Map(); //player points for the war

	def receive = {
	  case KOTHWarActor.StartWarMsg(war_id,event_id,map_id,duration,guild_ids,node_ids,koth_units,multiplier_threshold) =>
			this.war_id = war_id
			this.map_id = map_id 
			this.event_id = event_id
			this.duration = duration
			this.guild_ids = guild_ids
      this.node_ids = node_ids
      this.koth_units = koth_units
      this.multiplier_threshold = multiplier_threshold
      this.node_ids foreach{
        node_id =>
          nodes(node_id) = 0
      }
      
			context.system.scheduler.scheduleOnce(duration minutes,
					self,
					KOTHWarActor.EndWar)
					context.become(in_war)
					sender() ! new KOTHWarActor.WarStartedMsg(war_id)
		case _ =>  
      sender() ! KOTHWarActor.WarNotStartedError
      context.stop(self)
	}

	def in_war: Actor.Receive= {
		case KOTHWarActor.WarDeployMsg(node_id,guild_id,player_id,deployed_items) =>
			val deploy_summary = calculate_deploy_points_and_deploy_count(deployed_items)
			val deployed_points = deploy_summary._1;
			val num_deploys = deploy_summary._2;
			//apply multiplier 
			val deployed_points_multiplier = apply_multiplier(deployed_points)
  		var item_map_jobj = Json.obj()
			deployed_items foreach {
				tuples =>
  				item_map_jobj = item_map_jobj + (tuples._1.toString() -> JsNumber(tuples._2))
			}

			val json: JsValue = JsObject(Seq(
					"player_id" -> JsString(player_id),
					"event_id" -> JsNumber(this.event_id),
					"num_deploy" -> JsNumber(num_deploys),
					"deploy_points" -> JsNumber(deployed_points_multiplier),
					"item_map" -> item_map_jobj,
					"guild_id" -> JsString(guild_id)
					))


			val future_response =  WS.url("http://localhost/mwios/index.php/internal/kinghill/deploy/")
			.post(Json.toJson(json))
			val response = Await.result(future_response, timeout.duration)
			val body = response.body

			val server_resp:JsValue = Json.parse(body)
			if(server_resp.\("success").as[Boolean] == true){
				//save node total value
				if(nodes.exists(_._1 == node_id)){
					nodes(node_id) = nodes(node_id) + deployed_points;
				}else{
					nodes(node_id) = deployed_points;
				}

				//save guild deploy
				var new_entry = true
						for(i <- 0 until guil_points_per_node.size){
							if(guil_points_per_node(i)._1 == node_id && guil_points_per_node(i)._2 == guild_id){
								guil_points_per_node(i) = guil_points_per_node(i) copy(_3 = guil_points_per_node(i)._3 + deployed_points)
										new_entry = false;
							} 
						}
				if(new_entry){
					guil_points_per_node += ((node_id,guild_id,deployed_points))
				}

				//save guild deploy
				if(guilds_deploy.exists(_._1 == guild_id)){
					guilds_deploy(guild_id) = guilds_deploy(guild_id) copy(_1 = guilds_deploy(guild_id)._1 + deployed_points , _2 = guilds_deploy(guild_id)._2 + num_deploys)
				}else{
					guilds_deploy(guild_id) = (deployed_points_multiplier,num_deploys)
				}

				//save player_deploy
				if(player_deploy.exists(_._1 == player_id)){
					player_deploy(player_id) = player_deploy(player_id) copy(_1 = player_deploy(player_id)._1 + deployed_points , _2 = player_deploy(player_id)._2 + num_deploys)
				}else{
					player_deploy(player_id) = (deployed_points_multiplier,num_deploys)
				}

				var guild_points_on_node:Int = 0;
					for(i <- 0 until guil_points_per_node.size){
						if(guil_points_per_node(i)._1 == node_id && guil_points_per_node(i)._2 == guild_id){
							guild_points_on_node = guil_points_per_node(i)._3

						} 
					}

					sender() ! new KOTHWarActor.WarDeployResponseMsg(nodes(node_id),get_node_winner(node_id),guild_points_on_node,guilds_deploy(guild_id)._1,player_deploy(player_id)._1,deployed_points_multiplier)
			}else{
				sender() ! KOTHWarActor.WarErrorMsg("SERVER_ERROR")
			}
	  case KOTHWarActor.GetEventDetailMsg(player_id,guild_id)=>
       var return_json = get_generic_event_details ++ get_specific_event_details(player_id, guild_id)
       sender() ! KOTHWarActor.GetEventDetailResponseMsg(return_json)
		case KOTHWarActor.EndWar =>
  			val json: JsValue = get_generic_event_details
  			val future_response =  WS.url("http://localhost/mwios/index.php/internal/kinghill/end_war/")
  			.post(Json.toJson(json))
  			val response = Await.result(future_response, timeout.duration)
  			val body = response.body
  			val server_resp:JsValue = Json.parse(body)
  			is_war_over = true
  			context.become(war_ended)
		case _=> sender() ! KOTHWarActor.WarErrorMsg("ERROR")
	}
  
	def war_ended: Actor.Receive = {
		case KOTHWarActor.GetEventDetailMsg(player_id,guild_id)=>
			var return_json =  get_generic_event_details ++ get_specific_event_details(player_id, guild_id)
			sender() ! KOTHWarActor.GetEventDetailResponseMsg(return_json)
  	case _=> sender() ! KOTHWarActor.WarErrorMsg("WAR_ENDED")
	}


	def get_specific_event_details(player_id:String,guild_id:String): JsObject = {
		  val node_points_guild:ArrayBuffer[(Int,Int)] = ArrayBuffer();
			node_ids foreach {
				node_id => 
				var node_guild_points = 0
				for(i <- 0 until guil_points_per_node.size){
					if(guil_points_per_node(i)._1 == node_id && guil_points_per_node(i)._2 == guild_id){
						node_guild_points = guil_points_per_node(i)._3
					} 
				}
				node_points_guild += ((node_id,node_guild_points))
			}
			var player_deploy_points = 0;
			if(player_deploy.exists(_._1 == player_id)){
				player_deploy_points = player_deploy(player_id)._1
			}
			
			var has_to_progress_goal:Boolean = false
			if(is_war_over && !guild_progress_goals.exists(_ == guild_id)){
				has_to_progress_goal = true
				guild_progress_goals += guild_id
			}
			var guild_node_points_json:JsArray = Json.arr()
			  node_points_guild foreach {
    			node =>
      			var item:JsObject = Json.obj()
      			item = item + ("_explicitType" -> JsString("kinghill.ClientKingHillGuildNodePoints"))
      			item = item + ("node_id" -> JsString(node._1.toString()))
      			item = item + ("guild_points" -> JsNumber(node._2))
            item = item + ("time_created" -> JsNumber(System.currentTimeMillis()))
      			guild_node_points_json = guild_node_points_json ++ Json.arr(item)
		  }
										
                    
			val json: JsObject = JsObject(Seq(
					"node_points" -> guild_node_points_json,
					"player_deploy_points" -> JsNumber(player_deploy_points),
					"has_to_progress_goal" -> JsBoolean(has_to_progress_goal),
          "guild_id" -> JsString(guild_id)
					))
			json
		}
    def get_generic_event_details: JsObject = {
      val nodes_leader:ArrayBuffer[(Int,Int,Option[String])] = ArrayBuffer();
      node_ids foreach {
        node_id => 
        val node_leader = get_node_winner(node_id)
        nodes_leader += ((node_id,nodes(node_id),node_leader))
      }
      val guild_total_deploy_points: ArrayBuffer[(String,Int)] = ArrayBuffer()
          guild_ids foreach {
          id=>
          var num = 0
          if(guilds_deploy.exists(_._1 == id)){
            num = guilds_deploy(id)._1
          }
          guild_total_deploy_points += ((id,num))
        }
        val guild_num_deploy: ArrayBuffer[(String,Int)] = ArrayBuffer()
            guild_ids foreach {
            id=>
            var num = 0
            if(guilds_deploy.exists(_._1 == id)){
              num = guilds_deploy(id)._2
            }
            guild_num_deploy += ((id,num))
          }
          val guilds_control_points: Map[String,Int] = Map()
          node_ids foreach {
            node_id => 
              get_node_winner(node_id) match {
              case Some(node_leader) =>
                if(guilds_control_points.exists(_._1 == node_leader)){
                  guilds_control_points(node_leader) = guilds_control_points(node_leader) + nodes(node_id)
                }else{
                  guilds_control_points(node_leader) = nodes(node_id)
                }
                case None =>
              }
          }
            var node_leaders_json:JsArray =Json.arr()
                nodes_leader foreach {
                    node =>
                    var item:JsObject = Json.obj()
                    item = item + ("_explicitType" -> JsString("kinghill.ClientKingHillBattleNode"))
                    item = item + ("node_id" -> JsNumber(node._1))
                    item = item + ("points" -> JsNumber(node._2))
                    item = item + ("time_created" -> JsNumber(System.currentTimeMillis()))
                    node._3 match{
                    case Some(value) => item = item + ("winner" -> JsString(value))
                    case None => item = item + ("winner" -> JsNull)
                    }

                    node_leaders_json = node_leaders_json ++ Json.arr(item)
                  }
                    var guilds_deploy_points_map_json = Json.obj()
                        guild_total_deploy_points foreach {
                      tuples =>
                      guilds_deploy_points_map_json = guilds_deploy_points_map_json + (tuples._1.toString() -> JsNumber(tuples._2))
                    }
                    var guilds_num_deploy_map_json = Json.obj()
                        guild_num_deploy foreach {
                      tuples =>
                      guilds_num_deploy_map_json = guilds_num_deploy_map_json + (tuples._1.toString() -> JsNumber(tuples._2))
                    }
                    var guilds_control_points_map_json = Json.obj()
                        guilds_control_points foreach {
                      tuples =>
                      guilds_control_points_map_json = guilds_control_points_map_json + (tuples._1.toString() -> JsNumber(tuples._2))
                    }
                    var guild_ids_json:JsArray = Json.arr()
                    guild_ids foreach{
                      id =>
                        val item = JsString(id)
                        guild_ids_json = guild_ids_json ++ Json.arr(item)
                    }
                    
                    val json: JsObject = JsObject(Seq(
                        "_explicitType" -> JsString("kinghill.ClientKingHillBattle"),
                        "war_id" -> JsString(war_id),
                        "battle_round" -> JsNumber(1),
                        "battle_nodes" -> node_leaders_json,
                        "guilds_deploy_points_map" -> guilds_deploy_points_map_json,
                        "guilds_control_points_map" -> guilds_control_points_map_json,
                        "guilds_num_deploys_map" -> guilds_control_points_map_json,
                        "is_war_over" -> JsBoolean(is_war_over),
                        "guild_ids" -> guild_ids_json
                        ))
                        json
      }
        
  
			def get_node_winner(node_id:Int):Option[String] = {
					var winning_guild_id:String = null;
			var max_point_deployed:Int = -1;
			for(i <- 0 until guil_points_per_node.size){
				if(guil_points_per_node(i)._1 == node_id && guil_points_per_node(i)._3 > max_point_deployed){
					max_point_deployed = guil_points_per_node(i)._3
							winning_guild_id = guil_points_per_node(i)._2

				} 
			}
			if(winning_guild_id != null){
				return Some(winning_guild_id)
			}
			return None
			}

			def calculate_deploy_points_and_deploy_count(deployed_items:Map[Int,Int]): (Int,Int) = {
					var deployed_points:Int = 0;
			var num_deploys:Int = 0;
			deployed_items foreach {
				tuples =>
				deployed_points += koth_units(tuples._1)*tuples._2
				num_deploys += tuples._2
			}

			return (deployed_points,num_deploys)
			}

			def apply_multiplier(points:Int):Int = {
					return points;
			}

}

object KOTHWarActor{
	val props = Props[KOTHWarActor]
case class WarErrorMsg(message:String);
case object WarNotStartedError
case class StartWarMsg(war_id:String,eventi_id:Int,map_id:Int,duration:Int,guild_ids:List[String],node_ids:List[Int],koth_units:Map[Int,Int],multiplier_threshold:Map[Int,Double])
case class WarStartedMsg(war_id:String)

case class WarDeployMsg(node_id:Int,guild_id:String,player_id:String,deployed_items:Map[Int,Int])
case class WarDeployResponseMsg(node_points:Int,node_winner_guild_id:Option[String],guild_point_deoployed_on_node:Int,total_guild_points_on_war:Int,player_points_on_war:Int,player_deployed_points:Int)

case class GetEventDetailMsg(player_id:String,guild_id:String)
case class GetEventDetailResponseMsg(response:JsValue)
case object EndWar
}