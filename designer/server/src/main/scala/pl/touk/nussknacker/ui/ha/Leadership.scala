package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import cats.effect.std.Supervisor
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

trait Leadership extends LazyLogging {
  def haEnabled: Boolean
  def isLeader(): Boolean
  def instanceId: String

  private val leadershipAcquiredCallbacks: CopyOnWriteArrayList[CallbackState] = new CopyOnWriteArrayList()
  private val leadershipLostCallbacks: CopyOnWriteArrayList[CallbackState]     = new CopyOnWriteArrayList()

  @volatile private var started = false

  /** Registers a callback invoked once each time this node acquires leadership (false→true transition).
    * If the node is already a leader when [[startHeartbeat]] is called, the callback fires then.
    * If the previous invocation is still in-progress when leadership is re-acquired, the callback is skipped.
    * Callbacks must be registered before [[startHeartbeat]] is called.
    *
    * Long-running callbacks must re-check [[isLeader]] before each unit of work: leadership can be
    * lost without [[onLeadershipLost]] firing (see its note), and [[isLeader]] is the only reliable,
    * self-expiring fence.
    */
  final def onLeadershipAcquired(callback: IO[Unit]): IO[Unit] =
    registerCallback(leadershipAcquiredCallbacks, eventName = "acquired", callback)

  /** Registers a callback invoked once each time this node loses leadership (true→false transition).
    * If the previous invocation is still in-progress when leadership is lost again, the callback is skipped.
    * Callbacks must be registered before [[startHeartbeat]] is called.
    *
    * Best-effort, not a hard fence: this is NOT guaranteed to fire when leadership is lost due to a
    * renewal timeout or a hung heartbeat loop. In that case the lease simply expires and [[isLeader]]
    * starts returning false, but no lost callback is invoked.
    */
  final def onLeadershipLost(callback: IO[Unit]): IO[Unit] =
    registerCallback(leadershipLostCallbacks, eventName = "lost", callback)

  /** Starts the leadership heartbeat. Must be called after all callbacks are registered.
    * If this node is already a leader, acquired callbacks fire immediately before the heartbeat loop begins.
    * The returned Resource cancels the heartbeat, releases the lock, and cancels any in-flight callbacks on finalization.
    */
  final def startHeartbeat(): Resource[IO, Unit] =
    Resource.eval(IO { started = true }) >>
      Supervisor[IO].flatMap(doStartHeartbeat)

  final protected def notifyLeadershipAcquired(supervisor: Supervisor[IO]): IO[Unit] =
    IO(logger.info("Leadership acquired")) >> leadershipAcquiredCallbacks.asScala.toList.traverse_(_.fire(supervisor))

  final protected def notifyLeadershipLost(supervisor: Supervisor[IO]): IO[Unit] =
    IO(logger.info("Leadership lost")) >> leadershipLostCallbacks.asScala.toList.traverse_(_.fire(supervisor))

  final protected def notifyLeadershipLostAndWait(): IO[Unit] =
    leadershipLostCallbacks.asScala.toList.traverse_(_.fireAndWait())

  protected def doStartHeartbeat(supervisor: Supervisor[IO]): Resource[IO, Unit]

  private def registerCallback(
      callbacks: CopyOnWriteArrayList[CallbackState],
      eventName: String,
      callback: IO[Unit],
  ): IO[Unit] = IO.defer {
    if (started)
      IO.raiseError(new IllegalStateException("Callbacks must be registered before startHeartbeat() is called"))
    else IO(callbacks.add(new CallbackState(callback, eventName))).void
  }

  private class CallbackState(callback: IO[Unit], eventName: String) {
    private val inProgress = new AtomicBoolean(false)

    def fire(supervisor: Supervisor[IO]): IO[Unit] = runIfNotInProgress(supervisor.supervise(_).void)

    // Blocking: caller waits for callback to complete (used in stop)
    def fireAndWait(): IO[Unit] = runIfNotInProgress(identity)

    private def runIfNotInProgress(wrap: IO[Unit] => IO[Unit]): IO[Unit] = IO.defer {
      if (inProgress.compareAndSet(false, true))
        wrap(
          callback
            .handleError(ex => logger.error(s"Leadership $eventName callback failed", ex))
            .guarantee(IO(inProgress.set(false)))
        )
      else
        IO(logger.warn(s"Leadership $eventName callback still in progress, skipping re-fire"))
    }

  }

}

object Leadership extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock, clock: Clock): Resource[IO, Leadership] = {
    haMode match {
      case disabled: HaMode.Disabled =>
        Resource.pure(new SingleNodeLeadership(instanceId = disabled.instanceId))
      case enabled: HaMode.Enabled =>
        DistributedLeadership.resource(distributedLock, enabled, clock)
    }
  }

}

/** Single-node (non-HA) leadership: this node is always the leader.
  * [[onLeadershipLost]] callbacks are never invoked — by design, leadership is never lost in this mode.
  */
final class SingleNodeLeadership(override val instanceId: String) extends Leadership {
  override def haEnabled: Boolean  = { false }
  override def isLeader(): Boolean = { true }

  override protected def doStartHeartbeat(supervisor: Supervisor[IO]): Resource[IO, Unit] = {
    Resource.eval(notifyLeadershipAcquired(supervisor))
  }

}

object DistributedLeadership {

  def resource(
      distributedLock: DistributedLock,
      config: HaMode.Enabled,
      clock: Clock,
  ): Resource[IO, DistributedLeadership] = {
    val lock = new LeaderLock(distributedLock, config.leader.leaseDuration)
    Resource.pure(new DistributedLeadership(lock, config.leader, config.lockQueryTimeout, config.instanceId, clock))
  }

}

final class DistributedLeadership private (
    lock: LeaderLock,
    leaderConfig: HaMode.LeaderConfig,
    lockQueryTimeout: FiniteDuration,
    override val instanceId: String,
    clock: Clock,
) extends Leadership {

  private val state: AtomicReference[LeaderLockResult] = new AtomicReference(LeaderLockResult.NotAcquired)

  override val haEnabled: Boolean = true

  override def isLeader(): Boolean = isActiveLeader(state.get())

  override protected def doStartHeartbeat(supervisor: Supervisor[IO]): Resource[IO, Unit] = {
    Resource
      .make(
        tryAcquireLock(onFailureMessage = "Initial leader lock acquisition failed")
          .flatMap {
            case None         => IO.unit
            case Some(result) => updateLeadershipState(result, supervisor)
          }
          >> heartbeatLoop(supervisor).start
      )(fiber => fiber.cancel >> stop())
      .void
  }

  private def isActiveLeader(result: LeaderLockResult): Boolean = result match {
    case LeaderLockResult.Acquired(validUntil) => validUntil.isAfter(clock.instant())
    case LeaderLockResult.NotAcquired          => false
  }

  private def heartbeatLoop(supervisor: Supervisor[IO]): IO[Unit] = {
    IO.sleep(leaderConfig.heartbeatInterval) >>
      IO.uncancelable(_ => heartbeat(supervisor)) >>
      heartbeatLoop(supervisor)
  }

  private def heartbeat(supervisor: Supervisor[IO]): IO[Unit] = {
    tryAcquireLock(onFailureMessage = "Leader heartbeat check failed — retaining current leadership state").flatMap {
      case None         => IO.unit
      case Some(result) => updateLeadershipState(result, supervisor)
    }
  }

  private def tryAcquireLock(onFailureMessage: String): IO[Option[LeaderLockResult]] = {
    IO(logger.trace("Attempting to acquire/renew leader lock")) >>
      lock
        .acquireOrRenew()
        .timed
        .map { case (elapsed, result) =>
          logger.trace(s"acquireOrRenew completed in ${elapsed.toMillis}ms, acquired: ${result.isAcquired}")
          if (elapsed > lockQueryTimeout)
            logger.warn(s"acquireOrRenew took ${elapsed.toMillis}ms, expected < ${lockQueryTimeout.toMillis}ms")
          Some(result)
        }
        .handleError { ex =>
          logger.warn(onFailureMessage, ex)
          None
        }
  }

  private def updateLeadershipState(result: LeaderLockResult, supervisor: Supervisor[IO]): IO[Unit] = {
    for {
      wasLeader <- IO(state.getAndSet(result).isAcquired)
      _         <- if (result.isAcquired && !wasLeader) notifyLeadershipAcquired(supervisor) else IO.unit
      _         <- if (!result.isAcquired && wasLeader) notifyLeadershipLost(supervisor) else IO.unit
    } yield ()
  }

  private def stop(): IO[Unit] = {
    for {
      previous <- IO(state.getAndSet(LeaderLockResult.NotAcquired))
      wasLeader = isActiveLeader(previous)
      _        <- if (wasLeader) IO(logger.info("Leadership lost on shutdown")) else IO.unit
      released <- if (wasLeader && leaderConfig.releaseOnStop) releaseLock() else IO.pure(false)
      _        <- if (released) notifyLeadershipLostAndWait() else IO.unit
    } yield ()
  }

  private def releaseLock(): IO[Boolean] = {
    lock
      .release()
      .handleError { ex =>
        logger.warn("Failed to release leader lock on stop — lease will expire naturally", ex)
        false
      }
  }

}
