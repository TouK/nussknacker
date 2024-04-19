package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{ActorNotFound, ActorPath, ActorRef, ActorSystem}
import akka.pattern.{AskTimeoutException, gracefulStop}
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object Utils extends LazyLogging {

  private val GracefulStopTimeout         = 5 seconds
  private val ActorResolutionTimeout      = 5 seconds
  private val TotalActorResolutionTimeout = 16 seconds

  def runSafely(action: => Unit): Unit = try {
    action
  } catch {
    case t: Throwable => logger.error("Error occurred, but skipping it", t)
  }

  def gracefulStopActor(actorRef: ActorRef, actorSystem: ActorSystem): Unit = {
    logger.info(s"Gracefully stopping $actorRef")

    try {
      Await.result(
        gracefulStop(actorRef, GracefulStopTimeout),
        GracefulStopTimeout + (1 second)
      )
    } catch {
      case _: AskTimeoutException | _: TimeoutException =>
        throw new IllegalStateException(s"Failed to gracefully stop actor $actorRef within timeout")
    }

    logger.info(s"Gracefully stopped $actorRef, waiting for it's name to be freed")

    try {
      Await.result(
        waitUntilActorNameIsFree(actorRef.path, actorSystem),
        TotalActorResolutionTimeout
      )
    } catch {
      case _: TimeoutException =>
        throw new IllegalStateException(s"Failed to free actor path ${actorRef.path} within timeout")
    }

    logger.info(s"$actorRef is stopped and it's name is free")
  }

  private def waitUntilActorNameIsFree(actorPath: ActorPath, actorSystem: ActorSystem): Future[Unit] = {
    import actorSystem.dispatcher

    def waitLoop(): Future[Unit] = {
      val actorResolutionFuture =
        actorSystem.actorSelection(actorPath).resolveOne(ActorResolutionTimeout).map(_ => false)

      actorResolutionFuture
        .recover { case _: ActorNotFound => true }
        .flatMap {
          case true  => Future.successful(())
          case false =>
            // name is still taken, retry until it's free
            Thread.sleep(500)
            waitLoop()
        }
    }

    waitLoop()
  }

}
