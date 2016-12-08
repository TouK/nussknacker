package pl.touk.esp.engine.api.test

import java.util.UUID

import pl.touk.esp.engine.api.{Context, InterpreterMode, MetaData, ProcessListener}
import pl.touk.esp.engine.api.deployment.test.{InvocationResult, NodeResult, TestResults}

import scala.util.Try

case class ResultsCollectingListener(holderClass: String, runId: String) extends ProcessListener with Serializable {

  def results = ResultsCollectingListenerHolder.results(runId)

  def clean() = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData, mode: InterpreterMode) = {
    ResultsCollectingListenerHolder.updateResult(runId, nodeId, NodeResult(context))
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData) = {}

  override def expressionEvaluated(nodeId: String, expression: String, context: Context, processMetaData: MetaData, result: Any) = {}

  override def serviceInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, params: Map[String, Any], result: Try[Any]) = {
    ResultsCollectingListenerHolder.updateResult(runId, nodeId, InvocationResult(context,params))
  }

  override def sinkInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, param: Any) = {
    //TODO: zamiast output??
    ResultsCollectingListenerHolder.updateResult(runId, nodeId, InvocationResult(context, Map("output" -> param)))
  }

}


object ResultsCollectingListenerHolder {

  var results = Map[String, TestResults]()

  def registerRun = synchronized {
    val runId = UUID.randomUUID().toString
    results += (runId -> TestResults())
    ResultsCollectingListener(getClass.getCanonicalName, runId)
  }

  private[test] def updateResult(runId: String, nodeId: String, nodeResult: NodeResult) = synchronized {
    val runResult = results.getOrElse(runId, TestResults()).updateResult(nodeId, nodeResult)
    results += (runId -> runResult)
  }

  private[test] def updateResult(runId: String, nodeId: String, nodeResult: InvocationResult) = synchronized {
    val runResult = results.getOrElse(runId, TestResults()).updateResult(nodeId, nodeResult)
    results += (runId -> runResult)
  }

  def cleanResult(runId: String) = synchronized {
    results -= runId
  }
}

