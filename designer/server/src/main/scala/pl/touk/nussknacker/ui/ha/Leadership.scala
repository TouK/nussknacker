package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{ActorSystem, Cancellable}

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

trait Leadership {
  def isLeader(): Boolean
  def instanceId: Option[String]

  /** Registers a callback invoked once each time this node acquires leadership (false→true transition).
    * If already a leader at registration time, the callback fires immediately.
    */
  def onLeadershipAcquired(callback: () => Unit): Unit
}

object Leadership extends LazyLogging {

  def create(haMode: HaMode, distributedLock: DistributedLock, actorSystem: ActorSystem)(
      implicit executionContext: ExecutionContext
  ): Resource[IO, Leadership] =
    haMode match {
      case HaMode.Disabled => Resource.pure(NoOpLeadership)
      case enabled: HaMode.Enabled =>
        val leaderLock = new LeaderLock(distributedLock, enabled.leaderLeaseDuration)
        Resource.make(
          IO.fromFuture(IO(leaderLock.acquireOrRenew()))
            .handleError { ex =>
              logger.warn("Initial leader lock acquisition failed", ex)
              false
            }
            .map(initiallyLeader => new LeadershipService(leaderLock, enabled, actorSystem, initiallyLeader))
        )(service => IO.fromFuture(IO(service.stop())))
    }

}

object NoOpLeadership extends Leadership {
  override def isLeader(): Boolean                              = true
  override def instanceId: Option[String]                       = None
  override def onLeadershipAcquired(callback: () => Unit): Unit = callback()
}

class LeadershipService(
    lock: LeaderLock,
    config: HaMode.Enabled,
    actorSystem: ActorSystem,
    initiallyLeader: Boolean,
)(implicit ec: ExecutionContext)
    extends Leadership
    with LazyLogging {

  private val isCurrentLeader     = new AtomicBoolean(initiallyLeader)
  private val shuttingDown        = new AtomicBoolean(false)
  private val scheduledHeartbeat  = new AtomicReference[Cancellable](Cancellable.alreadyCancelled)
  private val inflightAcquire     = new AtomicReference[Future[Boolean]](Future.successful(false))
  private val leadershipCallbacks = new CopyOnWriteArrayList[() => Unit]

  scheduleHeartbeat()

  override def isLeader(): Boolean        = isCurrentLeader.get()
  override def instanceId: Option[String] = Some(config.instanceId)

  override def onLeadershipAcquired(callback: () => Unit): Unit = {
    val alreadyLeader = isCurrentLeader.get()
    leadershipCallbacks.add(callback)
    if (alreadyLeader) Future { callback() }
  }

  private def scheduleHeartbeat(): Unit = {
    val tick = actorSystem.scheduler.scheduleOnce(config.leaderHeartbeatInterval) {
      if (!shuttingDown.get()) {
        val acquire = lock.acquireOrRenew()
        inflightAcquire.set(acquire)
        acquire.onComplete { result =>
          inflightAcquire.set(Future.successful(false))
          result match {
            case Success(acquired) =>
              val wasLeader = isCurrentLeader.getAndSet(acquired)
              if (acquired && !wasLeader)
                // Callbacks are dispatched asynchronously so they never block the heartbeat thread.
                // A blocked heartbeat delays lock renewal, causing a spurious leader step-down.
                leadershipCallbacks.iterator().asScala.foreach(cb => Future { cb() })
            case Failure(ex) =>
              // Step down on any failure: a brief no-leader window is safer than split brain.
              if (isCurrentLeader.getAndSet(false)) {
                logger.warn("Leader heartbeat failed — relinquishing leadership", ex)
              }
          }
          if (!shuttingDown.get()) scheduleHeartbeat()
        }
      }
    }
    scheduledHeartbeat.set(tick)
  }

  def stop(): Future[Unit] = {
    shuttingDown.set(true)
    scheduledHeartbeat.get().cancel()
    // Wait for any in-flight acquireOrRenew before releasing: if release() runs first, the owner-bypass
    // in acquireOrRenew (WHERE locked_by = instanceId) would re-acquire the lock after it was released,
    // extending the lease by leaderLeaseDuration and leaving other nodes without a leader for that window.
    inflightAcquire.get().recover { case _ => false }.flatMap(_ => lock.release())
  }

}
