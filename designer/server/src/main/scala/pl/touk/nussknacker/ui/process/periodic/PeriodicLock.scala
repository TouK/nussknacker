package pl.touk.nussknacker.ui.process.periodic

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.ha.{DistributedLock, HaMode, NoOpDistributedLock}

import scala.concurrent.Future
import scala.concurrent.duration._

final class PeriodicLock(underlying: DistributedLock, val lockDuration: Option[FiniteDuration]) {
  private val name                      = "periodic-work"
  private val duration                  = lockDuration.getOrElse(0.seconds)
  def acquireOrRenew(): Future[Boolean] = underlying.acquireOrRenew(name, duration)
  def release(): Future[Boolean]        = underlying.release(name)
}

object PeriodicLock extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock): Resource[IO, PeriodicLock] =
    haMode match {
      case HaMode.Disabled(_) =>
        Resource.pure(new PeriodicLock(NoOpDistributedLock, None))
      case e: HaMode.Enabled =>
        Resource.make(IO.pure(new PeriodicLock(distributedLock, Some(e.periodicLockDuration))))(lock =>
          IO.fromFuture(IO(lock.release()))
            .handleError(ex =>
              logger.error("Failed to release periodic lock on stop — lease will expire naturally", ex)
            )
            .void
        )
    }

}
