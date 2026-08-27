package pl.touk.nussknacker.ui.process.periodic

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.ha.{DistributedLock, HaMode, Leadership}
import pl.touk.nussknacker.ui.ha.HaMode.PeriodicLockMode

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

sealed trait PeriodicDeploymentLock {
  def acquireOrRenew(): Future[Boolean]
  def release(): Future[Boolean]
  def lockDuration: Option[FiniteDuration]
}

final class NoOpPeriodicDeploymentLock extends PeriodicDeploymentLock {
  override def acquireOrRenew(): Future[Boolean]    = Future.successful(true)
  override def release(): Future[Boolean]           = Future.successful(true)
  override def lockDuration: Option[FiniteDuration] = None
}

final class DedicatedPeriodicDeploymentLock(underlying: DistributedLock, lockDuration: FiniteDuration)(
    implicit ec: ExecutionContext
) extends PeriodicDeploymentLock {
  private val name                                  = "periodic-process-deployment"
  override def acquireOrRenew(): Future[Boolean]    = underlying.acquireOrRenew(name, lockDuration).map(_.isDefined)
  override def release(): Future[Boolean]           = underlying.release(name)
  override def lockDuration: Option[FiniteDuration] = Some(lockDuration)
}

final class LeaderPeriodicDeploymentLock(leadership: Leadership) extends PeriodicDeploymentLock {
  override def acquireOrRenew(): Future[Boolean]    = Future.successful(leadership.isLeader())
  override def release(): Future[Boolean]           = Future.successful(true)
  override val lockDuration: Option[FiniteDuration] = None
}

object PeriodicDeploymentLock extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock, leadership: Leadership)(
      implicit ec: ExecutionContext
  ): Resource[IO, PeriodicDeploymentLock] =
    haMode match {
      case HaMode.Disabled(_) =>
        Resource.pure(new NoOpPeriodicDeploymentLock)
      case e: HaMode.Enabled =>
        e.periodicLockMode match {
          case PeriodicLockMode.Leader =>
            Resource.pure(new LeaderPeriodicDeploymentLock(leadership))
          case PeriodicLockMode.Dedicated =>
            Resource.make(IO.pure(new DedicatedPeriodicDeploymentLock(distributedLock, e.periodicLockDuration)))(lock =>
              IO.fromFuture(IO(lock.release()))
                .handleError(ex =>
                  logger.error("Failed to release periodic lock on stop — lease will expire naturally", ex)
                )
                .void
            )
        }
    }

}
