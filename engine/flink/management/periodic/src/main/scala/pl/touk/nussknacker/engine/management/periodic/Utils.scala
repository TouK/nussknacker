package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{ActorNotFound, ActorPath, ActorRef, ActorSystem}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object Utils extends LazyLogging {

  private val ActorResolutionTimeout = 10 seconds
  private val ActorResolutionPause   = 50 milliseconds
  private val ActorResolutionRetries = 50

  def runSafely(action: => Unit): Unit = try {
    action
  } catch {
    case t: Throwable => logger.error("Error occurred, but skipping it", t)
  }

  def stopActorAndWaitUntilItsNameIsFree(actorRef: ActorRef, actorSystem: ActorSystem): Unit = {
    import actorSystem.dispatcher
    logger.info(s"Stopping $actorRef")

    actorSystem.stop(actorRef)

    val freeNameFuture = for {
      _ <- waitUntilActorNameIsFree( // this step is necessary because gracefulStop does not guarantee that the supervisor is notified of the name being freed
        actorRef.path,
        actorSystem
      )
    } yield {}

    Await.result(
      freeNameFuture,
      ActorResolutionRetries * (ActorResolutionTimeout + ActorResolutionPause) + (1 second)
    )

    logger.info(s"Stopped $actorRef and ensured it's name is free")
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
