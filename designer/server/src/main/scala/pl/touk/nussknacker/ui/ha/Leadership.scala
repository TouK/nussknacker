package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import cats.implicits._
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
    * If the node is already a leader when [[startHeartbeat]] is called, the callback fires then.
    * If the previous invocation is still in-progress when leadership is re-acquired, the callback is skipped.
    * Callbacks must be registered before [[startHeartbeat]] is called.
    */
  def onLeadershipAcquired(callback: () => IO[Unit]): IO[Unit]

  /** Registers a callback invoked once each time this node loses leadership (true→false transition).
    * If the previous invocation is still in-progress when leadership is lost again, the callback is skipped.
    */
  def onLeadershipLost(callback: () => IO[Unit]): IO[Unit]

  /** Starts the leadership heartbeat. Must be called after all callbacks are registered.
    * If this node is already a leader, acquired callbacks fire immediately before the heartbeat loop begins.
    * The returned Resource cancels the heartbeat and releases the lock on finalization.
    */
  def startHeartbeat(): Resource[IO, Unit]
}

private[ha] class CallbackState(callback: () => IO[Unit], eventName: String) extends LazyLogging {
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

object Leadership extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock, clock: Clock): Resource[IO, Leadership] =
    haMode match {
      case disabled: HaMode.Disabled =>
        Resource.pure(new SingleNodeLeadership(instanceId = disabled.instanceId))
      case enabled: HaMode.Enabled =>
        DistributedLeadership.resource(distributedLock, enabled, clock)
    }

}

final class SingleNodeLeadership(override val instanceId: String) extends Leadership with LazyLogging {
  private val leadershipAcquiredCallbacks: CopyOnWriteArrayList[CallbackState] = new CopyOnWriteArrayList()

  override def isLeader(): Boolean  = true
  override def isHaEnabled: Boolean = false

  override def onLeadershipAcquired(callback: () => IO[Unit]): IO[Unit] =
    IO(leadershipAcquiredCallbacks.add(new CallbackState(callback, "acquired"))).void

  override def onLeadershipLost(callback: () => IO[Unit]): IO[Unit] = IO.unit

  override def startHeartbeat(): Resource[IO, Unit] =
    Resource.eval(leadershipAcquiredCallbacks.asScala.toList.traverse_(_.fire()))
}

object DistributedLeadership extends LazyLogging {

  def resource(
      distributedLock: DistributedLock,
      config: HaMode.Enabled,
      clock: Clock,
  ): Resource[IO, DistributedLeadership] = {
    val lock = new LeaderLock(distributedLock, config.leader.leaseDuration, clock)
    Resource.pure(new DistributedLeadership(lock, config.leader, config.instanceId, clock))
  }

}

final class DistributedLeadership private (
    lock: LeaderLock,
    leaderConfig: HaMode.LeaderConfig,
    override val instanceId: String,
    clock: Clock,
) extends Leadership
    with LazyLogging {

  private val state: AtomicReference[LeaderLockResult] = new AtomicReference(LeaderLockResult.NotAcquired)
  private val leadershipAcquiredCallbacks: CopyOnWriteArrayList[CallbackState] = new CopyOnWriteArrayList()
  private val leadershipLostCallbacks: CopyOnWriteArrayList[CallbackState]     = new CopyOnWriteArrayList()

  override def isLeader(): Boolean = state.get() match {
    case LeaderLockResult.Acquired(validUntil) => validUntil.isAfter(clock.instant())
    case LeaderLockResult.NotAcquired          => false
  }

  override val isHaEnabled: Boolean = true

  override def onLeadershipAcquired(callback: () => IO[Unit]): IO[Unit] =
    IO(leadershipAcquiredCallbacks.add(new CallbackState(callback, "acquired"))).void

  override def onLeadershipLost(callback: () => IO[Unit]): IO[Unit] =
    IO(leadershipLostCallbacks.add(new CallbackState(callback, "lost"))).void

  override def startHeartbeat(): Resource[IO, Unit] =
    Resource
      .make(
        lock
          .acquireOrRenew()
          .handleError { ex =>
            logger.warn("Initial leader lock acquisition failed", ex)
            LeaderLockResult.NotAcquired
          }
          .flatMap { initialResult =>
            IO(state.set(initialResult)) >>
              (if (initialResult.isAcquired) handleLeadershipAcquired() else IO.unit) >>
              heartbeatLoop.start
          }
      )(fiber => fiber.cancel >> stop())
      .void

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

  // Called after the heartbeat fiber is cancelled — see Resource.make in startHeartbeat
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

}
