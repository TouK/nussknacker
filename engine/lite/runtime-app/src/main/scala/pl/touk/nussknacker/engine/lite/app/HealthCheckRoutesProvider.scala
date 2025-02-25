package pl.touk.nussknacker.engine.lite.app

import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.management.scaladsl.PekkoManagement
import org.apache.pekko.pattern._
import org.apache.pekko.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.lite.RunnableScenarioInterpreter
import pl.touk.nussknacker.engine.lite.TaskStatus.{Running, TaskStatus}
import pl.touk.nussknacker.engine.lite.app.RunnableScenarioInterpreterStatusCheckerActor.GetStatus

import scala.concurrent.Future
import scala.concurrent.duration._

class HealthCheckRoutesProvider(system: ActorSystem, scenarioInterpreter: RunnableScenarioInterpreter) {

  system.actorOf(
    RunnableScenarioInterpreterStatusCheckerActor.props(scenarioInterpreter),
    RunnableScenarioInterpreterStatusCheckerActor.actorName
  )

  private val management = PekkoManagement(system)

  def routes: Route = management.routes
}

class InterpreterIsRunningCheck(system: ActorSystem) extends (() => Future[Boolean]) with LazyLogging {

  // default check timeout is 1sec so ask timeout should be lower to see details of error
  private implicit val askTimeout: Timeout = Timeout(900.millis)

  import system.dispatcher

  override def apply(): Future[Boolean] = {
    system.actorSelection(system / RunnableScenarioInterpreterStatusCheckerActor.actorName).ask(GetStatus).map {
      case status: TaskStatus =>
        logger.debug(s"Status is: $status")
        status == Running
    }
  }

}

class RunnableScenarioInterpreterStatusCheckerActor(scenarioInterpreter: RunnableScenarioInterpreter) extends Actor {

  override def receive: Receive = { case GetStatus =>
    context.sender() ! scenarioInterpreter.status()
  }

}

object RunnableScenarioInterpreterStatusCheckerActor {

  def props(scenarioInterpreter: RunnableScenarioInterpreter): Props =
    Props(new RunnableScenarioInterpreterStatusCheckerActor(scenarioInterpreter))

  def actorName = "interpreter-status-checker"

  case object GetStatus

}
