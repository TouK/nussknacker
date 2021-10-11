package pl.touk.nussknacker.engine.testmode

import cats.Monad
import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ToCollect
import cats.implicits._
import pl.touk.nussknacker.engine.resultcollector.{CollectableAction, ResultCollector, TransmissionNames}

import scala.language.higherKinds

class TestInvocationCollector(testRunId: TestRunId) extends ResultCollector {

  def collectWithResponse[A, F[_] : Monad](contextId: ContextId, nodeId: NodeId, request: => ToCollect, mockValue: Option[A], action: => F[CollectableAction[A]], names: TransmissionNames): F[A] = {
    mockValue match {
      case Some(mockVal) =>
        ResultsCollectingListenerHolder.updateResults(
          testRunId, _.updateMockedResult(nodeId.id, contextId, request)
        )
        mockVal.pure[F]
      case None =>
        action.map(_.result)
    }
  }

}

//TODO: this should be somehow expressed via ResultCollector/TestServiceInvocationCollector
case class SinkInvocationCollector(runId: TestRunId, nodeId: String) {

  def collect(context: Context, result: Any): Unit = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateMockedResult(nodeId, ContextId(context.id), result))
  }
} 
