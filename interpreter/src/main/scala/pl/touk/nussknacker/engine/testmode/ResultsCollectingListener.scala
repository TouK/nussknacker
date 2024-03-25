package pl.touk.nussknacker.engine.testmode

import java.util.UUID
import pl.touk.nussknacker.engine.api._
import TestProcess._
import io.circe.Json
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import scala.util.Try

object TestRunId {
  def generate: TestRunId = new TestRunId(UUID.randomUUID().toString)
}

case class TestRunId private (id: String)

//TODO: this class is passed explicitly in too many places, should be more tied to ResultCollector (maybe we can have listeners embedded there?)
case class ResultsCollectingListener[T](holderClass: String, runId: TestRunId, variableEncoder: Any => T)
    extends ProcessListener
    with Serializable {

  def results: TestResults[T] = ResultsCollectingListenerHolder.resultsForId(runId)

  def clean(): Unit = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateNodeResult(nodeId, context, variableEncoder))
  }

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {}

  override def deadEndEncountered(
      lastNodeId: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {}

  override def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expression: String,
      context: Context,
      processMetaData: MetaData,
      result: Any
  ): Unit = {
    ResultsCollectingListenerHolder.updateResults(
      runId,
      _.updateExpressionResult(nodeId, context, expressionId, result, variableEncoder)
    )
  }

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any]
  ): Unit = {}

  override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit =
    ResultsCollectingListenerHolder.updateResults(runId, _.updateExceptionResult(exceptionInfo, variableEncoder))

}

object ResultsCollectingListenerHolder {

  private var results = Map[TestRunId, TestResults[Any]]()

  // TODO: casting is not so nice, but currently no other idea...
  def resultsForId[T](id: TestRunId): TestResults[T] = results(id).asInstanceOf[TestResults[T]]

  def registerTestEngineListener: ResultsCollectingListener[Json] = synchronized {
    registerListener(TestInterpreterRunner.testResultsVariableEncoder)
  }

  def registerListener: ResultsCollectingListener[Any] = synchronized {
    registerListener(identity)
  }

  def cleanResult(runId: TestRunId): Unit = synchronized {
    results -= runId
  }

  private def registerListener[T](variableEncoder: Any => T): ResultsCollectingListener[T] = synchronized {
    val runId = TestRunId.generate
    results += (runId -> TestResults(Map(), Map(), Map(), List()))
    ResultsCollectingListener(getClass.getCanonicalName, runId, variableEncoder)
  }

  private[testmode] def updateResults(runId: TestRunId, action: TestResults[Any] => TestResults[Any]): Unit =
    synchronized {
      val current = results.getOrElse(runId, throw new IllegalArgumentException("Run was not registered..."))
      results += (runId -> action(current))
    }

}
