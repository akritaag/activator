/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import scala.concurrent.{ Future, ExecutionContext }
import controllers.ConsoleController
import play.api.Play.current

class ClientController extends Actor with ActorLogging {
  import ClientController._
  import ExecutionContext.Implicits.global

  context.system.scheduler.schedule(
    ConsoleController.config.getLong("console.update-frequency").milliseconds,
    ConsoleController.config.getLong("console.update-frequency").milliseconds,
    self,
    Tick)

  def receive = {
    case CreateClient(id) =>
      if (context.child(id).isEmpty) context.actorOf(Props[ClientHandler], id) forward InitializeCommunication
    case Tick => context.children foreach { _ ! Tick }
  }
}

object ClientController {
  case class CreateClient(id: String)
  case class Connection(ref: ActorRef, enum: Enumerator[JsValue])
  case class HandleRequest(payload: JsValue)
  case class Update(js: JsValue)
  case object InitializeCommunication
  case object Tick

  def join(id: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val timeout = Timeout(1.second)
    (ConsoleController.clientHandlerActor ? CreateClient(id)).map {
      case Connection(ref, enumerator) => (Iteratee.foreach[JsValue] { ref ! HandleRequest(_) }.map(_ => ref ! PoisonPill), enumerator)
    }
  }
}
