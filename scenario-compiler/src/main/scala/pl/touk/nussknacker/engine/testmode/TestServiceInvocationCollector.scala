package pl.touk.nussknacker.engine.testmode

import cats.Monad
import cats.implicits._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ToCollect, TransmissionNames}
import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

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
            nodeId.id,
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
              nodeId.id,
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

  def createSinkInvocationCollector(nodeId: String, ref: String): SinkInvocationCollector =
    new SinkInvocationCollector(resultsCollectingListener, nodeId, ref)

}

//TODO: this should be somehow expressed via ResultCollector/TestServiceInvocationCollector
final class SinkInvocationCollector(
    resultsCollectingListener: ResultsCollectingListener[_],
    nodeId: String,
    ref: String
) extends Serializable {

  def collect(context: Context, result: Any): Unit = {
    resultsCollectingListener.updateResults(
      _.updateExternalInvocationResult(
        nodeId,
        ContextId(context.id),
        ref,
        result,
        resultsCollectingListener.variableEncoder
      )
    )
  }

}
