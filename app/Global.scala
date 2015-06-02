import play.api._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import actors.KOTHWar.KOTHWarActorFactory

object Global extends GlobalSettings{
  override def onStart(app: Application) {
    val system = Akka.system
    system.actorOf(KOTHWarActorFactory.props,"KOTHWarActorFactory")
    Logger.info("KOTHWarActorFactory created")
  }
}