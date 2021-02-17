package pl.touk.nussknacker.engine.testmode

import java.util.UUID

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.TestProcess._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.testmode.Collectors.QueryServiceResult

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

  private[testmode] def updateResults(runId: TestRunId, action: TestResults[_] => TestResults[_]) = synchronized {
    val current = results.getOrElse(runId, throw new IllegalArgumentException("Run was not registered..."))
    results += (runId -> action(current))
  }

  def cleanResult(runId: TestRunId): Unit = synchronized {
    results -= runId
  }

}

private object QueryResultsHolder {

  private var queryResults = Map[TestRunId, List[QueryServiceResult]]()

  private[testmode] def queryResultsForId(id: TestRunId): List[QueryServiceResult] = {
    queryResults.getOrElse(id, List.empty)
  }

  private[testmode] def updateResult(runId: TestRunId, queryServiceResult: QueryServiceResult) = synchronized {
    val current = queryResults.getOrElse(runId, List.empty)
    queryResults += (runId -> (current ++ List(queryServiceResult)))
  }

  private[testmode] def cleanResult(runId: TestRunId): Unit = synchronized {
    queryResults -= runId
  }

}