package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.RescheduleFinishedActor.CheckStates

import scala.concurrent.duration._

object RescheduleFinishedActor {
  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    Props(new RescheduleFinishedActor(service, interval))
  }
  case object CheckStates
}

class RescheduleFinishedActor(service: PeriodicProcessService, interval: FiniteDuration) extends Actor with LazyLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    context.system.scheduler.schedule(initialDelay = Duration.Zero, interval = interval, receiver = self, message = CheckStates)
  }

  override def receive: Receive = {
    case CheckStates =>
      logger.debug("Checking processes to be rescheduled or marked as failed")
      service.handleFinished
  }

}
