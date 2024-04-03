package pl.touk.nussknacker.engine.testmode

import java.util.UUID
import pl.touk.nussknacker.engine.api._
import TestProcess._
import io.circe.Json
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import java.util.concurrent.ConcurrentSkipListMap
import scala.util.Try

case class TestRunId private (id: String) extends Comparable[TestRunId] {
  override def compareTo(other: TestRunId): Int = id.compareTo(other.id)
}

object TestRunId {
  def generate: TestRunId = new TestRunId(UUID.randomUUID().toString)

  def apply(id: String): TestRunId = throw new IllegalArgumentException("Please use generate instead of apply")
}

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

  private val results = new ConcurrentSkipListMap[TestRunId, TestResults[Any]]()

  // TODO: casting is not so nice, but currently no other idea...
  def resultsForId[T](id: TestRunId): TestResults[T] = results.get(id).asInstanceOf[TestResults[T]]

  def registerTestEngineListener: ResultsCollectingListener[Json] = {
    registerListener(TestInterpreterRunner.testResultsVariableEncoder)
  }

  def registerListener: ResultsCollectingListener[Any] = {
    registerListener(identity)
  }

  def cleanResult(runId: TestRunId): Unit = {
    results.remove(runId)
  }

  private def registerListener[T](variableEncoder: Any => T): ResultsCollectingListener[T] = {
    val runId = TestRunId.generate
    results.put(runId, TestResults(Map(), Map(), Map(), List()))
    ResultsCollectingListener(getClass.getCanonicalName, runId, variableEncoder)
  }

  private[testmode] def updateResults(runId: TestRunId, action: TestResults[Any] => TestResults[Any]): Unit = {
    Option {
      results.computeIfPresent(runId, (_: TestRunId, output: TestResults[Any]) => action(output))
    } match {
      case Some(_) =>
      case None =>
        throw new IllegalArgumentException("Run was not registered...")
    }
  }

}
