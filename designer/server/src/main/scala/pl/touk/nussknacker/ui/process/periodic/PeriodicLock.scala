package pl.touk.nussknacker.ui.process.periodic

import cats.effect.{IO, Resource}
import pl.touk.nussknacker.ui.ha.{DistributedLock, HaMode, NoOpDistributedLock}

import scala.concurrent.Future
import scala.concurrent.duration._

final class PeriodicLock(underlying: DistributedLock, duration: FiniteDuration) {
  private val name                      = "periodic-work"
  def acquireOrRenew(): Future[Boolean] = underlying.acquireOrRenew(name, duration)
  def release(): Future[Unit]           = underlying.release(name)
}

object PeriodicLock {

  def create(haMode: HaMode, distributedLock: DistributedLock): Resource[IO, PeriodicLock] =
    haMode match {
      case HaMode.Disabled =>
        Resource.pure(new PeriodicLock(NoOpDistributedLock, 0.seconds))
      case e: HaMode.Enabled =>
        Resource.make(IO.pure(new PeriodicLock(distributedLock, e.periodicLockDuration)))(lock =>
          IO.fromFuture(IO(lock.release()))
        )
    }

}
