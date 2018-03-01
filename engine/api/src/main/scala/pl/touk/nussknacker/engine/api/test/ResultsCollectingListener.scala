package pl.touk.nussknacker.engine.api.test

import java.util.UUID

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.test._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

import scala.util.Try

case class TestRunId(id: String)
  

//FIXME: extract traits and move most of stuff to interpreter, currently there is too much stuff in API...
case class ResultsCollectingListener(holderClass: String, runId: TestRunId) extends ProcessListener with Serializable {

  def results[T]: TestResults[T] = ResultsCollectingListenerHolder.resultsForId(runId)

  def clean() = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData) = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateNodeResult(nodeId, context))
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData) = {}

  override def expressionEvaluated(nodeId: String, expressionId: String, expression: String, context: Context, processMetaData: MetaData, result: Any) = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateExpressionResult(nodeId, context, expressionId, result))
  }

  override def serviceInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, params: Map[String, Any], result: Try[Any]) = {}

  override def sinkInvoked(nodeId: String, ref: String, context: Context, processMetaData: MetaData, param: Any) = {}

  override def exceptionThrown(exceptionInfo: EspExceptionInfo[_ <: Throwable]) = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateExceptionResult(exceptionInfo))
  }
}


object ResultsCollectingListenerHolder {

  private var results = Map[TestRunId, TestResults[_]]()

  //TODO: casting is not so nice, but currently no other idea...
  def resultsForId[T](id: TestRunId): TestResults[T] = results(id).asInstanceOf[TestResults[T]]

  def registerRun[T](variableEncoder: Any => T): ResultsCollectingListener = synchronized {
    val runId = TestRunId(UUID.randomUUID().toString)
    results += (runId -> new TestResults[T](Map(), Map(), Map(), List(), variableEncoder))
    ResultsCollectingListener(getClass.getCanonicalName, runId)
  }

  private[test] def updateResults(runId: TestRunId, action: TestResults[_] => TestResults[_]) = synchronized {
    val current = results.getOrElse(runId, throw new IllegalArgumentException("Run was not registered..."))
    results += (runId -> action(current))
  }

  def cleanResult(runId: TestRunId): Unit = synchronized {
    results -= runId
  }

}

object InvocationCollectors {

  case class NodeContext(contextId: String, nodeId: String, ref: String)

  case class ServiceInvocationCollector private(runIdOpt: Option[TestRunId], nodeContext: NodeContext) {
    def enable(runId: TestRunId) = this.copy(runIdOpt = Some(runId))
    def collectorEnabled = runIdOpt.isDefined

    def collect(testInvocation: Any): Unit = {
      if (collectorEnabled) {
        runIdOpt match {
          case Some(runId) =>
            //TODO: no variables available here?
            ResultsCollectingListenerHolder.updateResults(runId, _.updateMockedResult(nodeContext.nodeId, Context(nodeContext.contextId), nodeContext.ref, testInvocation))
          case None =>
            throw new IllegalStateException("RunId is not defined")
        }
      } else {
        throw new IllegalStateException("Collector is not enabled")
      }
    }
  }

  object ServiceInvocationCollector {
    def apply(nodeContext: NodeContext): ServiceInvocationCollector = {
      ServiceInvocationCollector(runIdOpt = None, nodeContext = nodeContext)
    }
  }

  case class SinkInvocationCollector(runId: TestRunId, nodeId: String, ref: String, outputPreparer: Any => String) {

    def collect(result: InterpretationResult): Unit = {
      val mockedResult = outputPreparer(result.output)
      ResultsCollectingListenerHolder.updateResults(runId, _.updateMockedResult(nodeId, result.finalContext, ref, mockedResult))
    }
  }

  case class SplitInvocationCollector(runId: TestRunId, nodeId: String) {

    def collect(result: InterpretationResult): Unit = {
      //TODO: should we pass variables here?
      ResultsCollectingListenerHolder.updateResults(runId, _.updateNodeResult(nodeId, Context(result.finalContext.id)))
    }
  }

}