package pl.touk.nussknacker.engine.api.test

import java.util.UUID

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.test.{ExpressionInvocationResult, MockedResult, NodeResult, TestResults}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

import scala.util.Try

case class TestRunId(id: String)
  
case class TestResultsEncoded[T](results: TestResults, encoded: T)

case class ResultsCollectingListener(holderClass: String, runId: TestRunId) extends ProcessListener with Serializable {

  def results[T]: TestResultsEncoded[T] = ResultsCollectingListenerHolder.resultsForId(runId)

  def clean() = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData) = {
    ResultsCollectingListenerHolder.updateResult(runId, nodeId, NodeResult(context))
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData) = {}

  override def expressionEvaluated(nodeId: String, expressionId: String, expression: String, context: Context, processMetaData: MetaData, result: Any) = {
    ResultsCollectingListenerHolder.updateResult(runId, nodeId, ExpressionInvocationResult(context, expressionId, result))
  }

  override def serviceInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, params: Map[String, Any], result: Try[Any]) = {}

  override def sinkInvoked(nodeId: String, ref: String, context: Context, processMetaData: MetaData, param: Any) = {}

  override def exceptionThrown(exceptionInfo: EspExceptionInfo[_ <: Throwable]) = {
    ResultsCollectingListenerHolder.updateResult(runId, exceptionInfo)
  }
}


object ResultsCollectingListenerHolder {

  private case class TestResultsWithEncoded[T](results: TestResultsEncoded[T], encoder: TestResults => T) {

    //This is workaround for Json encoding + classloader problem. When Flink job is finishing the user code classloader is closed. After that no new class can be loaded. This can lead to some peculiar 
    //classloading errors while printing test results in UI (we try to marshall object to json, EncodeJson wants to load some class, it's not possible, ...). Therefore we do encoding here while the job is running
    //TODO: this can be a bit inefficient - maybe we should try to encode only when we have to?
    def update(action: TestResults => TestResults): TestResultsWithEncoded[T] = {
      val newResult = action(results.results)
      val newEncoded = TestResultsEncoded(newResult, encoder(newResult))
      copy(results = newEncoded)
    }
  }

  private var results = Map[TestRunId, TestResultsWithEncoded[_]]()

  //TODO: casting is not so nice, but currently no other idea...
  def resultsForId[T](id: TestRunId): TestResultsEncoded[T] = results(id).results.asInstanceOf[TestResultsEncoded[T]]

  def registerRun[T](encoder: TestResults => T): ResultsCollectingListener = synchronized {
    val runId = TestRunId(UUID.randomUUID().toString)
    val newResult = TestResults()
    val encoded = TestResultsWithEncoded(TestResultsEncoded(newResult, encoder(newResult)), encoder)
    results += (runId -> encoded)
    ResultsCollectingListener(getClass.getCanonicalName, runId)
  }

  private[test] def updateResult(runId: TestRunId, nodeId: String, nodeResult: NodeResult) = synchronized {
    updateResults(runId, _.updateResult(nodeId, nodeResult))
  }

  private[test] def updateResult(runId: TestRunId, espExceptionInfo: EspExceptionInfo[_ <: Throwable]) = synchronized {
    updateResults(runId, _.updateResult(espExceptionInfo))
  }

  private[test] def updateResult(runId: TestRunId, nodeId: String, nodeResult: ExpressionInvocationResult) = synchronized {
    updateResults(runId, _.updateResult(nodeId, nodeResult))
  }

  private[test] def updateResult(runId: TestRunId, nodeId: String, mockedResult: MockedResult) = synchronized {
    updateResults(runId, _.updateResult(nodeId, mockedResult))
  }

  private def updateResults(runId: TestRunId, action: TestResults => TestResults) = {
    val current = results.getOrElse(runId, throw new IllegalArgumentException("Run was not registered..."))
    results += (runId -> current.update(action))
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
            val mockedResult = MockedResult(Context(nodeContext.contextId), nodeContext.ref, testInvocation)
            ResultsCollectingListenerHolder.updateResult(runId, nodeContext.nodeId, mockedResult)
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
      ResultsCollectingListenerHolder.updateResult(runId, nodeId, MockedResult(result.finalContext, ref, mockedResult))
    }
  }

  case class SplitInvocationCollector(runId: TestRunId, nodeId: String) {

    def collect(result: InterpretationResult): Unit = {
      ResultsCollectingListenerHolder.updateResult(runId, nodeId, NodeResult(result.finalContext.copy(variables = Map.empty)))
    }
  }

}