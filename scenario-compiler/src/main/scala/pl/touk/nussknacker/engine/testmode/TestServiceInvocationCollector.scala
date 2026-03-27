package pl.touk.nussknacker.engine.testmode

import cats.Monad
import cats.implicits._
import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ToCollect, TransmissionNames}
import pl.touk.nussknacker.engine.resultcollector.{ResultCollector, SinkInvocationCollector}

import scala.language.higherKinds

class TestServiceInvocationCollector(resultsCollectingListener: ResultsCollectingListener[_]) extends ResultCollector {

  override def collectWithResponse[A, F[_]: Monad](
      contextId: ContextId,
      nodeId: NodeId,
      serviceRef: String,
      request: => ToCollect,
      mockValue: Option[A],
      action: => F[CollectableAction[A]],
      names: TransmissionNames
  ): F[A] = {
    mockValue match {
      case Some(mockVal) =>
        resultsCollectingListener.updateResults(
          _.updateExternalInvocationResult(
            nodeId,
            contextId,
            serviceRef,
            request,
            resultsCollectingListener.variableEncoder
          )
        )
        mockVal.pure[F]
      case None =>
        action.map { case CollectableAction(resultToCollect, result) =>
          val invocationResult = Map("request" -> request, "response" -> resultToCollect())
          resultsCollectingListener.updateResults(
            _.updateExternalInvocationResult(
              nodeId,
              contextId,
              serviceRef,
              invocationResult,
              resultsCollectingListener.variableEncoder
            )
          )
          result
        }
    }
  }

  override def createSinkInvocationCollector(nodeId: NodeId, ref: String): Option[SinkInvocationCollector] =
    Some(new TestSinkInvocationCollector(resultsCollectingListener, nodeId, ref))

}

//TODO: this should be somehow expressed via ResultCollector/TestServiceInvocationCollector
final class TestSinkInvocationCollector(
    resultsCollectingListener: ResultsCollectingListener[_],
    nodeId: NodeId,
    ref: String
) extends SinkInvocationCollector {

  def collect(context: Context, result: Any): Unit = {
    val normalizedResult = SinkInvocationCollector.normalizeCollectedResult(result)
    resultsCollectingListener.updateResults(
      _.updateExternalInvocationResult(
        nodeId,
        context.id,
        ref,
        normalizedResult,
        resultsCollectingListener.variableEncoder
      )
    )
  }

}
