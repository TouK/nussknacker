package pl.touk.nussknacker.engine.util

import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import scala.concurrent.Future

object WithInvocationCollector {

  def apply[A](toCollect: => A)(action: => Future[Unit])(implicit collector: ServiceInvocationCollector): Future[Unit] = {
    WithInvocationCollector.apply[A, Unit](toCollect, ())(action)
  }

  def apply[A, B](toCollect: => A, toReturn: B)(action: => Future[B])(implicit collector: ServiceInvocationCollector): Future[B] = {
    if (collector.collectorEnabled) {
      collector.collect(toCollect)
      Future.successful(toReturn)
    } else {
      action
    }
  }
}