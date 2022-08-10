package pl.touk.nussknacker.engine.lite.kafka

import akka.Done
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.model.Uri
import akka.management.scaladsl.AkkaManagement
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.lite.RunnableScenarioInterpreter
import pl.touk.nussknacker.engine.lite.TaskStatus.{Running, TaskStatus}
import pl.touk.nussknacker.engine.lite.kafka.RunnableScenarioInterpreterStatusCheckerActor.GetStatus

import scala.concurrent.Future
import scala.concurrent.duration._

class HealthCheckServerRunner(system: ActorSystem, scenarioInterpreter: RunnableScenarioInterpreter) {

  private  val management = AkkaManagement(system)

  def start(): Future[Uri] = {
    system.actorOf(RunnableScenarioInterpreterStatusCheckerActor.props(scenarioInterpreter), RunnableScenarioInterpreterStatusCheckerActor.actorName)
    management.start()
  }

  def stop(): Future[Done] = {
    management.stop()
  }

}

class KafkaRuntimeRunningCheck(system: ActorSystem) extends (() => Future[Boolean]) with LazyLogging {

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

  override def receive: Receive = {
    case GetStatus =>
      context.sender() ! scenarioInterpreter.status()
  }

}

object RunnableScenarioInterpreterStatusCheckerActor {

  def props(scenarioInterpreter: RunnableScenarioInterpreter): Props =
    Props(new RunnableScenarioInterpreterStatusCheckerActor(scenarioInterpreter))

  def actorName = "interpreter-status-checker"

  case object GetStatus

}