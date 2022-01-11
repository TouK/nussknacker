package pl.touk.nussknacker.engine.lite.kafka

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.model.Uri
import akka.management.scaladsl.AkkaManagement
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.lite.kafka.KafkaScenarioInterpreterStatusCheckerActor.GetStatus
import pl.touk.nussknacker.engine.lite.kafka.TaskStatus.{Running, TaskStatus}

import scala.concurrent.Future
import scala.concurrent.duration._

class HealthCheckServerRunner(system: ActorSystem, scenarioInterpreter: KafkaTransactionalScenarioInterpreter) {

  def start(): Future[Uri] = {
    system.actorOf(KafkaScenarioInterpreterStatusCheckerActor.props(scenarioInterpreter), KafkaScenarioInterpreterStatusCheckerActor.actorName)
    AkkaManagement(system).start()
  }

}

class KafkaRuntimeRunningCheck(system: ActorSystem) extends (() => Future[Boolean]) with LazyLogging {

  // default check timeout is 1sec so ask timeout should be lower to see details of error
  private implicit val askTimeout: Timeout = Timeout(900.millis)

  import system.dispatcher

  override def apply(): Future[Boolean] = {
    system.actorSelection(system / KafkaScenarioInterpreterStatusCheckerActor.actorName).ask(GetStatus).map {
      case status: TaskStatus =>
        logger.debug(s"Status is: $status")
        status == Running
    }
  }

}

class KafkaScenarioInterpreterStatusCheckerActor(scenarioInterpreter: KafkaTransactionalScenarioInterpreter) extends Actor {

  override def receive: Receive = {
    case GetStatus =>
      context.sender() ! scenarioInterpreter.status()
  }

}

object KafkaScenarioInterpreterStatusCheckerActor {

  def props(scenarioInterpreter: KafkaTransactionalScenarioInterpreter): Props =
    Props(new KafkaScenarioInterpreterStatusCheckerActor(scenarioInterpreter))

  def actorName = "interpreter-status-checker"

  case object GetStatus

}