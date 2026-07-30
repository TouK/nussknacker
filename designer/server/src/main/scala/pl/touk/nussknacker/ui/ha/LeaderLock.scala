package pl.touk.nussknacker.ui.ha

import cats.effect.IO

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

sealed abstract class LeaderLockResult {
  val isAcquired: Boolean
}

object LeaderLockResult {

  case object NotAcquired extends LeaderLockResult {
    override val isAcquired: Boolean = false
  }

  final case class Acquired(validUntil: Instant) extends LeaderLockResult {
    override val isAcquired: Boolean = true
  }

}

final class LeaderLock(underlying: DistributedLock, duration: FiniteDuration) {

  private val name = "designer-leader"

  def acquireOrRenew(): IO[LeaderLockResult] =
    IO.fromFuture(IO(underlying.acquireOrRenew(name, duration))).map {
      case Some(validUntil) => LeaderLockResult.Acquired(validUntil)
      case None             => LeaderLockResult.NotAcquired
    }

  def release(): IO[Boolean] =
    IO.fromFuture(IO(underlying.release(name)))

}
