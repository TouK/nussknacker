package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{ActorNotFound, ActorPath, ActorRef, ActorSystem}
import akka.pattern.{AskTimeoutException, gracefulStop}
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object Utils extends LazyLogging {

  private val GracefulStopTimeout    = 10 seconds
  private val ActorResolutionTimeout = 10 seconds
  private val ActorResolutionPause   = 50 milliseconds
  private val ActorResolutionRetries = 400

  def runSafely(action: => Unit): Unit = try {
    action
  } catch {
    case t: Throwable => logger.error("Error occurred, but skipping it", t)
  }

  def gracefulStopActor(actorRef: ActorRef, actorSystem: ActorSystem): Unit = {
    import actorSystem.dispatcher
    logger.info(s"Gracefully stopping $actorRef")

    val gracefulStopFuture = for {
      _ <- gracefulStop(actorRef, GracefulStopTimeout)
      _ <- waitUntilActorNameIsFree( // this step is necessary because gracefulStop does not guarantee that the supervisor is notified of the name being freed
        actorRef.path,
        actorSystem
      )
    } yield {}

    Await.result(
      gracefulStopFuture,
      GracefulStopTimeout + ActorResolutionRetries * (ActorResolutionTimeout + ActorResolutionPause) + (1 second)
    )

    logger.info(s"Gracefully stopped $actorRef")
  }

  private def waitUntilActorNameIsFree(actorPath: ActorPath, actorSystem: ActorSystem)(implicit e: ExecutionContext) = {
    retry
      .Pause(ActorResolutionRetries, ActorResolutionPause)
      .apply { () =>
        val actorResolutionFuture =
          actorSystem
            .actorSelection(actorPath)
            .resolveOne(ActorResolutionTimeout)
            .map(_ => Left(s"Actor path $actorPath is still taken"))

        actorResolutionFuture.recover { case _: ActorNotFound =>
          Right(s"Actor path $actorPath is free")
        }
      }
      .map {
        case Left(_)  => throw new IllegalStateException(s"Failed to free actor path $actorPath within allowed retries")
        case Right(_) => ()
      }
  }

}
