package pl.touk.nussknacker.engine.api.test

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.CollectorMode.CollectorMode
import pl.touk.nussknacker.engine.api.{ContextId, InterpretationResult}

import scala.concurrent.{ExecutionContext, Future}

object InvocationCollectors {

  type ToCollect = Any

  case class NodeContext(contextId: String, nodeId: String, ref: String, outputVariableNameOpt: Option[String])

  case class QueryServiceResult(name: String, result: Any)

  case class TransmissionNames(invocationName: String, resultName: String)
  object TransmissionNames {
    val default = TransmissionNames("invocation", "result")
  }
  case class CollectableAction[A](toCollect: () => ToCollect, result: A)

  private[test] object CollectorMode extends Enumeration {
    type CollectorMode = Value
    val Test, Query, Production = Value
  }

  type ServiceInvocationCollectorForContext = ContextId => ServiceInvocationCollector

  trait ServiceInvocationCollector {

    def enable(runId: TestRunId): ServiceInvocationCollector
    protected def runIdOpt: Option[TestRunId]
    protected def collectorMode: CollectorMode
    protected def updateResult(runId: TestRunId, testInvocation: Any, name: String): Unit

    def collect[A](request: => ToCollect, mockValue: Option[A])(action: => Future[A], names: TransmissionNames = TransmissionNames.default)
                  (implicit ec: ExecutionContext): Future[A] = {
      collectWithResponse[A](request, mockValue)(action.map(a => CollectableAction(() => "", a)), names)
    }

    def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(action: => Future[CollectableAction[A]], names: TransmissionNames = TransmissionNames.default)
                              (implicit ec: ExecutionContext): Future[A] = {
      def enabledMode = if (runIdOpt.isDefined) collectorMode else CollectorMode.Production
      def runId = runIdOpt.getOrElse(throw new IllegalStateException(s"Collector has to be enabled in $enabledMode"))

      enabledMode match {
        case CollectorMode.Production =>
          action.map(_.result)
        case CollectorMode.Test => mockValue match {
          case Some(mockVal) =>
            updateResult(runId, request, names.invocationName)
            Future.successful(mockVal)
          case None =>
            action.map(_.result)
        }
        case CollectorMode.Query =>
          updateResult(runId, request, names.invocationName)
          action.map { collectableAction =>
            updateResult(runId, collectableAction.toCollect(), names.resultName)
            collectableAction.result
          }
      }
    }
  }

  case class QueryServiceInvocationCollector private(runIdOpt: Option[TestRunId], serviceName: String) extends ServiceInvocationCollector {
    def enable(runId: TestRunId) = this.copy(runIdOpt = Some(runId))
    override protected def collectorMode: CollectorMode = CollectorMode.Query

    override protected def updateResult(runId: TestRunId, testInvocation: Any, name: String): Unit = {
      val queryServiceResult = QueryServiceResult(name, testInvocation)
      QueryResultsHolder.updateResult(runId, queryServiceResult)
    }

    def getResults[T]: List[QueryServiceResult] = {
      withRunIdDefined { runId =>
        QueryResultsHolder.queryResultsForId(runId)
      }
    }

    def cleanResults(): Unit = {
      withRunIdDefined { runId =>
        QueryResultsHolder.cleanResult(runId)
      }
    }

    private def withRunIdDefined[T](f: (TestRunId) => T): T = {
      runIdOpt match {
        case Some(runId) => f(runId)
        case None => throw new IllegalStateException("RunId is not defined")
      }
    }

  }

  case class TestServiceInvocationCollector private(runIdOpt: Option[TestRunId],
                                                    contextId: ContextId,
                                                    nodeId: NodeId, serviceRef: String) extends ServiceInvocationCollector {
    def enable(runId: TestRunId) = this.copy(runIdOpt = Some(runId))
    override protected def collectorMode: CollectorMode = CollectorMode.Test

    override protected def updateResult(runId: TestRunId, testInvocation: Any, name: String): Unit = {
      ResultsCollectingListenerHolder.updateResults(
        runId, _.updateMockedResult(nodeId.id, contextId, serviceRef, testInvocation)
      )
    }
  }

  object TestServiceInvocationCollector {
    def apply(contextId: ContextId, nodeId: NodeId, serviceRef: String): TestServiceInvocationCollector = {
      TestServiceInvocationCollector(runIdOpt = None, contextId, nodeId, serviceRef)
    }
  }

  object QueryServiceInvocationCollector {
    def apply(serviceName: String): QueryServiceInvocationCollector = {
      QueryServiceInvocationCollector(runIdOpt = None, serviceName = serviceName)
    }
  }

  case class SinkInvocationCollector(runId: TestRunId, nodeId: String, ref: String, outputPreparer: Any => String) {

    def collect(result: InterpretationResult): Unit = {
      val mockedResult = outputPreparer(result.output)
      ResultsCollectingListenerHolder.updateResults(runId, _.updateMockedResult(nodeId, ContextId(result.finalContext.id), ref, mockedResult))
    }
  }

}