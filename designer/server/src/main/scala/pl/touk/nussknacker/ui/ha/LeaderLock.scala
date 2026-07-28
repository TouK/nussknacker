package pl.touk.nussknacker.ui.ha

import cats.effect.IO

import java.time.{Clock, Instant}
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

final class LeaderLock(underlying: DistributedLock, duration: FiniteDuration, clock: Clock) {

  private val name = "designer-leader"

  def acquireOrRenew(): IO[LeaderLockResult] =
    for {
      // validUntil is measured before the DB call — conservative by design: the actual DB-side
      // lock_until is slightly later (LOCALTIMESTAMP + duration), but accounting for call duration
      // as "lost time" protects isLeader() from returning true after the lock has expired.
      validUntil <- IO(clock.instant().plusMillis(duration.toMillis))
      result <- IO.fromFuture(IO(underlying.acquireOrRenew(name, duration))).map {
        case true  => LeaderLockResult.Acquired(validUntil)
        case false => LeaderLockResult.NotAcquired
      }
    } yield result

  def release(): IO[Boolean] =
    IO.fromFuture(IO(underlying.release(name)))

}
