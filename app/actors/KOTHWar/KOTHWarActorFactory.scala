package actors.KOTHWar

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import scala.collection.mutable.Map
import play.api.Logger


class KOTHWarActorFactory extends Actor{
   val actorMap:Map[String,ActorRef] = Map()
  def receive = {
    case KOTHWarActorFactory.GetOrCreateMSG(war_id)=>
      if(actorMap.contains(war_id)){
        sender() ! actorMap.get(war_id).get
      }else{
          val actor = context.actorOf(KOTHWarActor.props,"kothwar_"+war_id)
          actorMap.put(war_id, actor)
          sender() ! actor
      }
    case KOTHWarActorFactory.RemoveWarMsg(war_id) =>
      actorMap -= war_id
  }
}

object KOTHWarActorFactory{
  val props = Props[KOTHWarActorFactory]
  case class GetOrCreateMSG(war_id:String)
  case class RemoveWarMsg(war_id:String)
}