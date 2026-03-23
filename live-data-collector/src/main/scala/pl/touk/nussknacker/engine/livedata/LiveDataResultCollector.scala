package pl.touk.nussknacker.engine.livedata

import cats.Monad
import cats.implicits._
import io.circe.Json
import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ToCollect, TransmissionNames}
import pl.touk.nussknacker.engine.resultcollector.{ResultCollector, SinkInvocationCollector}
import pl.touk.nussknacker.engine.testmode.TestInterpreterRunner

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.language.higherKinds

class LiveDataResultCollector(
    processName: ProcessName,
    maxNumberOfRecords: Int,
    retentionTime: Duration,
    throughputTimeWindow: Duration,
) extends ResultCollector {

  private val variableEncoder: Any => Json = TestInterpreterRunner.testResultsVariableEncoder

  override def collectWithResponse[A, F[_]: Monad](
      contextId: ContextId,
      nodeId: NodeId,
      serviceRef: String,
      request: => ToCollect,
      mockValue: Option[A],
      action: => F[CollectableAction[A]],
      names: TransmissionNames
  ): F[A] = action.map(_.result)

  override def createSinkInvocationCollector(nodeId: NodeId, ref: String): Option[SinkInvocationCollector] =
    Some((context: Context, result: Any) => {
      val normalizedResult = SinkInvocationCollector.normalizeCollectedResult(result)
      LiveDataCollectingListenerStorageHolder.withStorage(
        processName,
        maxNumberOfRecords,
        retentionTime,
        throughputTimeWindow
      )(
        _.addExternalServiceInvocation(
          nodeId,
          ExternalServiceInvocationResult(context.id, Instant.now(), ref, variableEncoder(normalizedResult))
        )
      )
    })

  override def shouldRegisterSinkInAdditionToCollector: Boolean = true

}
