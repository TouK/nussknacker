package pl.touk.esp.engine.standalone.metrics

import cats.data.NonEmptyList
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.standalone.utils.metrics.WithEspTimers
import pl.touk.esp.engine.util.service.EspTimer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait InvocationMetrics extends WithEspTimers {

  override protected def instantTimerWindowInSeconds = 20

  private val nodeErrorTimers : collection.concurrent.TrieMap[String, EspTimer] = collection.concurrent.TrieMap()

  //TODO: a moze po prostu var przypisywany na open?
  private lazy val succesTimer = espTimer("success")

  def measureTime[T](invocation: => Future[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], T]])(implicit ec: ExecutionContext) :
    Future[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], T]]= {
    val start = System.nanoTime()
    try {
      val future = invocation
      future.onComplete {
        case Success(Left(errors)) => errors.toList.foreach(ex => markErrorTimer(start, ex.nodeId))
        case Success(Right(_)) => succesTimer.update(start)
        case Failure(e) => markErrorTimer(start)
      }
      future
    } catch {
      case NonFatal(e) => markErrorTimer(start); throw e
    }
  }

  private def markErrorTimer(startTime: Long, nodeId: Option[String] = None) : Unit = {
    val id = nodeId.getOrElse("unknown")
    nodeErrorTimers.getOrElseUpdate(id, espTimer(id)).update(startTime)
  }
}
