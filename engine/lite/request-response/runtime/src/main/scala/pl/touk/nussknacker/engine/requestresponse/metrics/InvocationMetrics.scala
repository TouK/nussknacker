package pl.touk.nussknacker.engine.requestresponse.metrics

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.util.metrics.MetricIdentifier
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag
import pl.touk.nussknacker.engine.util.service.EspTimer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class InvocationMetrics(context: EngineRuntimeContext) {

  protected val instantTimerWindowInSeconds = 20

  private val nodeErrorTimers: collection.concurrent.TrieMap[String, EspTimer] = collection.concurrent.TrieMap()

  //TODO: maybe var initialized in `open`?
  private lazy val successTimer = espTimer(Map(), NonEmptyList.of("invocation", "success"))

  def measureTime[T](invocation: => Future[ValidatedNel[ErrorType, T]])(implicit ec: ExecutionContext):
  Future[ValidatedNel[ErrorType,  T]] = {
    val start = System.nanoTime()
    try {
      val future = invocation
      future.onComplete {
        case Success(Invalid(errors)) => errors.toList.foreach(ex => markErrorTimer(start, ex.nodeComponentInfo.map(_.nodeId)))
        case Success(Valid(_)) => successTimer.update(start)
        case Failure(e) => markErrorTimer(start)
      }
      future
    } catch {
      case NonFatal(e) => markErrorTimer(start); throw e
    }
  }

  private def markErrorTimer(startTime: Long, nodeId: Option[String] = None): Unit = {
    val id = nodeId.getOrElse("unknown")
    nodeErrorTimers.getOrElseUpdate(id, espTimer(Map(nodeIdTag -> id), NonEmptyList.of("invocation", "failure"))).update(startTime)
  }

  private def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer = context
    .metricsProvider.espTimer(MetricIdentifier(name, tags), instantTimerWindowInSeconds)

}
