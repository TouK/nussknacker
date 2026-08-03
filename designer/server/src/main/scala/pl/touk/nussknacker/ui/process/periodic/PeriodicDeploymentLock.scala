package pl.touk.nussknacker.ui.process.periodic

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.ha.{DistributedLock, HaMode, NoOpDistributedLock}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

final class PeriodicDeploymentLock(underlying: DistributedLock, val lockDuration: Option[FiniteDuration])(
    implicit ec: ExecutionContext
) {
  private val name                      = "periodic-process-deployment"
  private val duration                  = lockDuration.getOrElse(0.seconds)
  def acquireOrRenew(): Future[Boolean] = underlying.acquireOrRenew(name, duration).map(_.isDefined)
  def release(): Future[Boolean]        = underlying.release(name)
}

object PeriodicDeploymentLock extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock)(
      implicit ec: ExecutionContext
  ): Resource[IO, PeriodicDeploymentLock] =
    haMode match {
      case HaMode.Disabled(_) =>
        Resource.pure(new PeriodicDeploymentLock(NoOpDistributedLock, None))
      case e: HaMode.Enabled =>
        Resource.make(IO.pure(new PeriodicDeploymentLock(distributedLock, Some(e.periodicLockDuration))))(lock =>
          IO.fromFuture(IO(lock.release()))
            .handleError(ex =>
              logger.error("Failed to release periodic lock on stop — lease will expire naturally", ex)
            )
            .void
        )
    }

}
