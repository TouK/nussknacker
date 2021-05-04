package pl.touk.nussknacker.engine.standalone.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.api.StandaloneContext
import pl.touk.nussknacker.engine.util.service.EspTimer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait InvocationMetrics {

  def context: StandaloneContext

  protected val instantTimerWindowInSeconds = 20

  private val nodeErrorTimers : collection.concurrent.TrieMap[String, EspTimer] = collection.concurrent.TrieMap()

  //TODO: maybe var initialized in `open`?
  private lazy val successTimer = espTimer(Map(), NonEmptyList.of("invocation", "success"))

  def measureTime[T](invocation: => Future[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], T]])(implicit ec: ExecutionContext) :
    Future[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], T]]= {
    val start = System.nanoTime()
    try {
      val future = invocation
      future.onComplete {
        case Success(Left(errors)) => errors.toList.foreach(ex => markErrorTimer(start, ex.nodeId))
        case Success(Right(_)) => successTimer.update(start)
        case Failure(e) => markErrorTimer(start)
      }
      future
    } catch {
      case NonFatal(e) => markErrorTimer(start); throw e
    }
  }

  private def markErrorTimer(startTime: Long, nodeId: Option[String] = None) : Unit = {
    val id = nodeId.getOrElse("unknown")
    nodeErrorTimers.getOrElseUpdate(id, espTimer(Map("nodeId" -> id), NonEmptyList.of("invocation", "failure"))).update(startTime)
  }

  private def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer = context.espTimer(instantTimerWindowInSeconds, tags, name)

}
