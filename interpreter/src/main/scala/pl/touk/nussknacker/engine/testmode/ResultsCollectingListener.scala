package pl.touk.nussknacker.engine.testmode

import java.util.UUID

import pl.touk.nussknacker.engine.api._
import TestProcess._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import scala.util.Try

object TestRunId {
  def generate: TestRunId = new TestRunId(UUID.randomUUID().toString)
}

case class TestRunId private (id: String)

//TODO: this class is passed explicitly in too many places, should be more tied to ResultCollector (maybe we can have listeners embedded there?)
case class ResultsCollectingListener(holderClass: String, runId: TestRunId) extends ProcessListener with Serializable {

  def results: TestResults = ResultsCollectingListenerHolder.resultsForId(runId)

  def clean(): Unit = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    ResultsCollectingListenerHolder.updateResults(runId, _.updateNodeResult(nodeId, context))
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
      _.updateExpressionResult(nodeId, context, expressionId, result)
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
    ResultsCollectingListenerHolder.updateResults(runId, _.updateExceptionResult(exceptionInfo))

}

object ResultsCollectingListenerHolder {

  private var results = Map[TestRunId, TestResults]()

  // TODO: casting is not so nice, but currently no other idea...
  def resultsForId(id: TestRunId): TestResults = results(id)

  def registerRun: ResultsCollectingListener = synchronized {
    val runId = TestRunId.generate
    results += (runId -> TestResults(Map(), Map(), Map(), List()))
    ResultsCollectingListener(getClass.getCanonicalName, runId)
  }

  private[testmode] def updateResults(runId: TestRunId, action: TestResults => TestResults): Unit = synchronized {
    val current = results.getOrElse(runId, throw new IllegalArgumentException("Run was not registered..."))
    results += (runId -> action(current))
  }

  def cleanResult(runId: TestRunId): Unit = synchronized {
    results -= runId
  }

}
