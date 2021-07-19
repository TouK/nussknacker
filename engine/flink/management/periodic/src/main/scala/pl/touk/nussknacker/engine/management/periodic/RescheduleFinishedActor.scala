package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, Props, Timers}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.RescheduleFinishedActor.CheckStates

import scala.concurrent.duration._

object RescheduleFinishedActor {
  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    Props(new RescheduleFinishedActor(service, interval))
  }
  case object CheckStates
}

class RescheduleFinishedActor(service: PeriodicProcessService, interval: FiniteDuration) extends Actor
  with Timers
  with LazyLogging {

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startPeriodicTimer(key = "checkStates", msg = CheckStates, interval = interval)
  }

  override def receive: Receive = {
    case CheckStates =>
      logger.debug("Checking scenarios to be rescheduled or marked as failed")
      service.handleFinished
  }

}
