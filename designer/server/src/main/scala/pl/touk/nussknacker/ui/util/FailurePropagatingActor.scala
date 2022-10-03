package pl.touk.nussknacker.ui.util

import akka.actor.{Actor, Status}

// see: https://medium.com/@linda0511ny/error-handling-in-akka-actor-with-future-ded3da0579dd
trait FailurePropagatingActor extends Actor {
  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    sender() ! Status.Failure(reason)
  }
}
