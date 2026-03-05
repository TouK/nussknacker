package pl.touk.nussknacker.engine.requestresponse.metrics

import cats.Monad
import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.util.metrics.MetricIdentifier
import pl.touk.nussknacker.engine.util.metrics.common.naming.{nodeIdTag, nodeNameTag}
import pl.touk.nussknacker.engine.util.service.EspTimer

import scala.language.higherKinds
import scala.util.control.NonFatal

class InvocationMetrics(context: EngineRuntimeContext) {

  protected val instantTimerWindowInSeconds = 20

  private val nodeErrorTimers: collection.concurrent.TrieMap[NodeId, EspTimer] = collection.concurrent.TrieMap()

  // TODO: maybe var initialized in `open`?
  private lazy val successTimer = espTimer(Map(), NonEmptyList.of("invocation", "success"))

  def measureTime[T, Effect[_]: Monad](
      invocation: => Effect[ValidatedNel[ErrorType, T]]
  ): Effect[ValidatedNel[ErrorType, T]] = {
    val start = System.nanoTime()
    try {
      implicitly[Monad[Effect]].map(invocation) { result =>
        result match {
          case Invalid(errors) => errors.toList.foreach(ex => markErrorTimer(start, ex.nodeComponentInfo))
          case Valid(_)        => successTimer.update(start)
        }
        result
      }
    } catch {
      case NonFatal(e) => markErrorTimer(start); throw e
    }
  }

  private def markErrorTimer(startTime: Long, nodeInfo: Option[NodeComponentInfo] = None): Unit = {
    val id       = nodeInfo.map(_.nodeId).getOrElse(NodeId("unknown"))
    val nodeName = nodeInfo.map(_.nodeName).getOrElse(NodeName(id.value))
    nodeErrorTimers
      .getOrElseUpdate(
        id,
        espTimer(Map(nodeIdTag -> id.value, nodeNameTag -> nodeName.value), NonEmptyList.of("invocation", "failure"))
      )
      .update(startTime)
  }

  private def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer =
    context.metricsProvider.espTimer(MetricIdentifier(name, tags), instantTimerWindowInSeconds)

}
