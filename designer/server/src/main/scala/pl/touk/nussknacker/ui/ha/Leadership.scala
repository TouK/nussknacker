package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import cats.implicits.toFoldableOps
import com.typesafe.scalalogging.LazyLogging

import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.jdk.CollectionConverters._

trait Leadership {
  def isLeader(): Boolean
  def instanceId: String
  def isHaEnabled: Boolean

  /** Registers a callback invoked once each time this node acquires leadership (false→true transition).
    * If already a leader at registration time, the callback fires immediately.
    * If the previous invocation is still in-progress when leadership is re-acquired, the callback is skipped.
    */
  def onLeadershipAcquired(callback: () => IO[Unit]): IO[Unit]

  /** Registers a callback invoked once each time this node loses leadership (true→false transition).
    * If the previous invocation is still in-progress when leadership is lost again, the callback is skipped.
    */
  def onLeadershipLost(callback: () => IO[Unit]): IO[Unit]
}

object Leadership extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock, clock: Clock): Resource[IO, Leadership] =
    haMode match {
      case disabled: HaMode.Disabled =>
        Resource.pure(new NoOpLeadership(instanceId = disabled.instanceId))
      case enabled: HaMode.Enabled =>
        LeadershipService.resource(distributedLock, enabled, clock)
    }

}

final class NoOpLeadership(override val instanceId: String) extends Leadership with LazyLogging {
  override def isLeader(): Boolean  = true
  override def isHaEnabled: Boolean = false
  override def onLeadershipAcquired(callback: () => IO[Unit]): IO[Unit] =
    IO.defer(callback().handleError(ex => logger.error("Leadership acquired callback failed", ex)).start.void)
  override def onLeadershipLost(callback: () => IO[Unit]): IO[Unit] = IO.unit
}

object LeadershipService extends LazyLogging {

  def resource(
      distributedLock: DistributedLock,
      config: HaMode.Enabled,
      clock: Clock,
  ): Resource[IO, LeadershipService] = {
    val lock = new LeaderLock(distributedLock, config.leader.leaseDuration, clock)
    for {
      initialResult <- Resource.eval(
        lock
          .acquireOrRenew()
          .handleError { ex =>
            logger.warn("Initial leader lock acquisition failed", ex)
            LeaderLockResult.NotAcquired
          }
      )
      service = new LeadershipService(lock, config.leader, config.instanceId, initialResult, clock)
      _ <- Resource.make(service.heartbeatLoop.start)(_.cancel >> service.stop())
    } yield service
  }

}

final class LeadershipService private (
    lock: LeaderLock,
    leaderConfig: HaMode.LeaderConfig,
    override val instanceId: String,
    initialResult: LeaderLockResult,
    clock: Clock,
) extends Leadership
    with LazyLogging {

  private val state: AtomicReference[LeaderLockResult]                         = new AtomicReference(initialResult)
  private val leadershipAcquiredCallbacks: CopyOnWriteArrayList[CallbackState] = new CopyOnWriteArrayList()
  private val leadershipLostCallbacks: CopyOnWriteArrayList[CallbackState]     = new CopyOnWriteArrayList()

  override def isLeader(): Boolean = state.get() match {
    case LeaderLockResult.Acquired(validUntil) => validUntil.isAfter(clock.instant())
    case LeaderLockResult.NotAcquired          => false
  }

  override val isHaEnabled: Boolean = true

  override def onLeadershipAcquired(callback: () => IO[Unit]): IO[Unit] = IO.defer {
    val cs = new CallbackState(callback, "acquired")
    leadershipAcquiredCallbacks.add(cs)
    if (isLeader()) cs.fire() else IO.unit
  }

  override def onLeadershipLost(callback: () => IO[Unit]): IO[Unit] =
    IO(leadershipLostCallbacks.add(new CallbackState(callback, "lost"))).void

  // poll(sleep) is cancellable; heartbeat is not — fiber.cancel waits for in-flight acquireOrRenew
  private def heartbeatLoop: IO[Unit] =
    IO.uncancelable { poll =>
      for {
        _ <- poll(IO.sleep(leaderConfig.heartbeatInterval))
        _ <- heartbeat
      } yield ()
    } >> heartbeatLoop

  private def heartbeat: IO[Unit] =
    for {
      result    <- tryAcquireLock
      wasLeader <- IO(state.getAndSet(result).isAcquired)
      _         <- if (result.isAcquired && !wasLeader) handleLeadershipAcquired() else IO.unit
      _         <- if (!result.isAcquired && wasLeader) handleLeadershipLost() else IO.unit
    } yield ()

  private def tryAcquireLock: IO[LeaderLockResult] =
    lock
      .acquireOrRenew()
      .handleError { ex =>
        logger.warn("Leader heartbeat failed — relinquishing leadership", ex)
        LeaderLockResult.NotAcquired
      }

  private def handleLeadershipAcquired(): IO[Unit] =
    IO(logger.info("Leadership acquired")) >> leadershipAcquiredCallbacks.asScala.toList.traverse_(_.fire())

  private def handleLeadershipLost(): IO[Unit] =
    IO(logger.info("Leadership lost")) >> leadershipLostCallbacks.asScala.toList.traverse_(_.fire())

  // Must be called after the heartbeat fiber is cancelled — see Resource.make in LeadershipService.resource
  private def stop(): IO[Unit] =
    for {
      wasLeader <- IO(state.getAndSet(LeaderLockResult.NotAcquired).isAcquired)
      released  <- if (wasLeader && leaderConfig.releaseOnStop) releaseLock else IO.pure(false)
      _         <- if (released) notifyLeadershipLostAndWait() else IO.unit
    } yield ()

  private def releaseLock: IO[Boolean] =
    lock
      .release()
      .handleError { ex =>
        logger.warn("Failed to release leader lock on stop — lease will expire naturally", ex)
        false
      }

  private def notifyLeadershipLostAndWait(): IO[Unit] =
    leadershipLostCallbacks.asScala.toList.traverse_(_.fireAndWait())

  private class CallbackState(callback: () => IO[Unit], eventName: String) {
    private val inProgress = new AtomicBoolean(false)

    // Non-blocking: callback runs in a new fiber, caller returns immediately
    def fire(): IO[Unit] = runIfNotInProgress(_.start.void)

    // Blocking: caller waits for callback to complete (used in stop)
    def fireAndWait(): IO[Unit] = runIfNotInProgress(identity)

    private def runIfNotInProgress(wrap: IO[Unit] => IO[Unit]): IO[Unit] = IO.defer {
      if (inProgress.compareAndSet(false, true))
        wrap(
          callback()
            .handleError(ex => logger.error(s"Leadership $eventName callback failed", ex))
            .guarantee(IO(inProgress.set(false)))
        )
      else
        IO(logger.warn(s"Leadership $eventName callback still in progress, skipping re-fire"))
    }

  }

}
