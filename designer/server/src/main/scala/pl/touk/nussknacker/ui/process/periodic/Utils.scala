package pl.touk.nussknacker.ui.process.periodic

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object Utils extends LazyLogging {

  private val ActorCreationPause   = 50 milliseconds
  private val ActorCreationRetries = 50

  def runSafely(action: => Unit): Unit = try {
    action
  } catch {
    case t: Throwable => logger.error("Error occurred, but skipping it", t)
  }

  def createActorWithRetry(actorName: String, props: Props, actorSystem: ActorSystem)(
      implicit ec: ExecutionContext
  ): ActorRef = {
    val actorRefFuture = retry
      .Pause(ActorCreationRetries, ActorCreationPause)
      .apply { () =>
        Future {
          Try(actorSystem.actorOf(props, actorName))
        }
      }
      .map {
        case Failure(ex) =>
          throw new IllegalStateException(s"Failed to create actor '$actorName' within allowed retries: $ex")
        case Success(a) => a
      }

    Await.result(actorRefFuture, ActorCreationRetries * ActorCreationPause + (1 second))
  }

}
