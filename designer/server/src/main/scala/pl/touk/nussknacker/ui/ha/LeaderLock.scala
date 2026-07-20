package pl.touk.nussknacker.ui.ha

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

final class LeaderLock(underlying: DistributedLock, duration: FiniteDuration) {
  private val name                      = "designer-leader"
  def acquireOrRenew(): Future[Boolean] = underlying.acquireOrRenew(name, duration)
  def release(): Future[Unit]           = underlying.release(name)
}
