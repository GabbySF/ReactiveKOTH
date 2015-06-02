package controllers

import play.api._
import play.api.mvc._
import views.html._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import akka.util.Timeout
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext.Implicits.global
import actors.KOTHWar.KOTHWarActorFactory
import akka.pattern.ask
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent._
import actors.KOTHWar.KOTHWarActor
import scala.concurrent.duration._
import play.api.libs.json.JsValue
import play.api.libs.json._
import scala.collection.mutable.Map
import play.api.libs.ws.WS

object Application extends Controller {
  implicit val timeout:Timeout = Timeout(Duration(5, SECONDS))
  
  def start_war = Action.async {
    request =>
      val start_war_json:JsValue = Json.parse(request.body.asText.get)
      val war_id = start_war_json.\("war_id").as[String]
      val map_id = start_war_json.\("map_id").as[Int]
      val event_id = start_war_json.\("event_id").as[Int]
      val duration = start_war_json.\("duration").as[Int]
      val guild_ids = start_war_json.\("guild_ids").as[List[String]]
      val node_ids = start_war_json.\("node_ids").as[List[Int]]
      val units_map_input =  start_war_json.\("units_map").as[JsObject].fields
      val units_map:Map[Int,Int] = Map();
      units_map_input foreach {
        item =>
          units_map(item._1.toInt) = item._2.as[Int] 
      }
      val multiplier_map_input =  start_war_json.\("multiplier_map").as[JsObject].fields
      val multiplier_map:Map[Int,Double] = Map();
      multiplier_map_input foreach {
        item =>
          multiplier_map(item._1.toInt) = item._2.as[Double] 
      }
      val msg = new KOTHWarActor.StartWarMsg(war_id,event_id,map_id,duration,guild_ids,node_ids,units_map,multiplier_map)
      val system =Akka.system
        system.actorSelection("*/KOTHWarActorFactory").resolveOne() flatMap {
          actorRef:ActorRef =>
            (actorRef ? KOTHWarActorFactory.GetOrCreateMSG(war_id)).mapTo[ActorRef] flatMap{
              warRef:ActorRef =>
                (warRef ? msg) flatMap {
                  case response:KOTHWarActor.WarStartedMsg => 
                    val json: JsValue = JsObject(Seq(
                        "response" -> JsString("true"),
                        "war_id" -> JsString(response.war_id)
                        ))
                    Future(Ok(Json.toJson(json)))
                  case error:KOTHWarActor.WarErrorMsg =>
                     val json: JsValue = JsObject(Seq(
                        "response" -> JsString("false"),
                        "msg" -> JsString(error.message)
                        ))
                    Future(Ok(Json.toJson(json)))
                }
                
            }
        }
  }
  
  def deploy = Action.async {
    request=>
      val start_war_json:JsValue = Json.parse(request.body.asText.get)
      val war_id = start_war_json.\("war_id").as[String]
      val guild_id = start_war_json.\("guild_id").as[String]
      val player_id = start_war_json.\("player_id").as[String]
      val node_id = start_war_json.\("node_id").as[Int]
      val item_map_input =  start_war_json.\("item_map").as[JsObject].fields
      val item_map:Map[Int,Int] = Map();
      item_map_input foreach {
        item =>
          item_map(item._1.toInt) = item._2.as[Int] 
      }
      val msg = new KOTHWarActor.WarDeployMsg(node_id,guild_id,player_id,item_map)
       val system =Akka.system
        system.actorSelection("*/KOTHWarActorFactory").resolveOne() flatMap {
          actorRef:ActorRef =>
            (actorRef ? KOTHWarActorFactory.GetOrCreateMSG(war_id)).mapTo[ActorRef] flatMap{
              warRef:ActorRef =>
                (warRef ? msg) flatMap {
                  case response:KOTHWarActor.WarDeployResponseMsg =>
                    val json: JsValue = JsObject(Seq(
                        "response" -> JsString("true"),
                        "node_points" -> JsNumber(response.node_points),
                        "node_winner_guild_id" -> JsString(response.node_winner_guild_id.get),
                        "guild_point_deployed_on_node" -> JsNumber(response.guild_point_deoployed_on_node),
                        "total_guild_points_on_war" -> JsNumber(response.total_guild_points_on_war),
                        "player_points_on_war" -> JsNumber(response.player_points_on_war),
                        "player_deployed_points" -> JsNumber(response.player_deployed_points)
                        ))
                    Future(Ok(Json.toJson(json)))
                case error:KOTHWarActor.WarErrorMsg =>
                     val json: JsValue = JsObject(Seq(
                        "response" -> JsString("false"),
                        "msg" -> JsString(error.message)
                        ))
                    Future(Ok(Json.toJson(json)))    
                }
            }
      }    
  }
  
    def get_event_details = Action.async {
    request=>
      val start_war_json:JsValue = Json.parse(request.body.asText.get)
      val war_id = start_war_json.\("war_id").as[String]
      val guild_id = start_war_json.\("guild_id").as[String]
      val player_id = start_war_json.\("player_id").as[String]
      val msg = new KOTHWarActor.GetEventDetailMsg(player_id,guild_id)
       val system =Akka.system
        system.actorSelection("*/KOTHWarActorFactory").resolveOne() flatMap {
          actorRef:ActorRef =>
            (actorRef ? KOTHWarActorFactory.GetOrCreateMSG(war_id)).mapTo[ActorRef] flatMap{
              warRef:ActorRef =>
                (warRef ? msg) flatMap {
                  case response:KOTHWarActor.GetEventDetailResponseMsg =>
                    Future(Ok(Json.toJson(response.response)))
                  case error:KOTHWarActor.WarErrorMsg =>
                     val json: JsValue = JsObject(Seq(
                        "response" -> JsString("false"),
                        "msg" -> JsString(error.message)
                        ))
                    Future(Ok(Json.toJson(json)))    
                  case KOTHWarActor.WarNotStartedError =>
                    actorRef ! KOTHWarActorFactory.RemoveWarMsg(war_id)
                    val json: JsValue = JsObject(Seq(
                        "response" -> JsString("false"),
                        "msg" -> JsString("WAR_NOT_STARTED")
                        ))
                    Future(Ok(Json.toJson(json)))   
                }
            }
      }    
  }
}