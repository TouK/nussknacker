package pl.touk.nussknacker.engine.testmode

import cats.Monad
import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ToCollect, TransmissionNames}
import cats.implicits._
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.language.higherKinds

class TestServiceInvocationCollector(testRunId: TestRunId) extends ResultCollector {

  override def collectWithResponse[A, F[_] : Monad](contextId: ContextId, nodeId: NodeId, serviceRef: String, request: => ToCollect, mockValue: Option[A], action: => F[CollectableAction[A]], names: TransmissionNames): F[A] = {
    mockValue match {
      case Some(mockVal) =>
        ResultsCollectingListenerHolder.updateResults(
          testRunId, _.updateMockedResult(nodeId.id, contextId, serviceRef, request)
        )
        mockVal.pure[F]
      case None =>
        action.map(_.result)
    }
  }
}

//TODO: this should be somehow expressed via ResultCollector/TestServiceInvocationCollector
case class SinkInvocationCollector(runId: TestRunId, nodeId: String, ref: String) {

  def collect(context: Context, result: Any): Unit = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateMockedResult(nodeId, ContextId(context.id), ref, result))
  }
}
