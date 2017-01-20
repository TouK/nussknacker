package pl.touk.esp.engine.api.test

import java.util.UUID

import pl.touk.esp.engine.api.{Context, InterpreterMode, MetaData, ProcessListener}
import pl.touk.esp.engine.api.deployment.test.{ExpressionInvocationResult, MockedResult, NodeResult, TestResults}
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.api.process.Sink

import scala.util.Try

case class ResultsCollectingListener(holderClass: String, runId: String) extends ProcessListener with Serializable {

  def results = ResultsCollectingListenerHolder.results(runId)

  def clean() = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData, mode: InterpreterMode) = {
    if (mode == InterpreterMode.Traverse) {
      ResultsCollectingListenerHolder.updateResult(runId, nodeId, NodeResult(context))
    }
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

  private[test] var results = Map[String, TestResults]()

  def registerRun = synchronized {
    val runId = UUID.randomUUID().toString
    results += (runId -> TestResults())
    ResultsCollectingListener(getClass.getCanonicalName, runId)
  }

  private[test] def updateResult(runId: String, nodeId: String, nodeResult: NodeResult) = synchronized {
    val runResult = results.getOrElse(runId, TestResults()).updateResult(nodeId, nodeResult)
    results += (runId -> runResult)
  }

  private[test] def updateResult(runId: String, espExceptionInfo: EspExceptionInfo[_ <: Throwable]) = synchronized {
    val runResult = results.getOrElse(runId, TestResults()).updateResult(espExceptionInfo)
    results += (runId -> runResult)
  }

  private[test] def updateResult(runId: String, nodeId: String, nodeResult: ExpressionInvocationResult) = synchronized {
    val runResult = results.getOrElse(runId, TestResults()).updateResult(nodeId, nodeResult)
    results += (runId -> runResult)
  }

  private[test] def updateResult(runId: String, nodeId: String, mockedResult: MockedResult) = synchronized {
    val runResult = results.getOrElse(runId, TestResults()).updateResult(nodeId, mockedResult)
    results += (runId -> runResult)
  }

  def cleanResult(runId: String) = synchronized {
    results -= runId
  }
}

object InvocationCollectors {

  case class NodeContext(contextId: String, nodeId: String, ref: String)

  case class ServiceInvocationCollector private(runIdOpt: Option[String], nodeContext: NodeContext) {
    def enable(runId: String) = this.copy(runIdOpt = Some(runId))
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

  case class SinkInvocationCollector(runId: String) {

    def collect(value: Any, nodeContext: NodeContext, originalSink: Sink): Unit = {
      originalSink.testDataOutput match {
        case Some(mapping) =>
          val mockedResult = mapping(value)
          ResultsCollectingListenerHolder.updateResult(runId, nodeContext.nodeId,
            MockedResult(Context(nodeContext.contextId), nodeContext.ref, mockedResult))
        case None =>
          throw new IllegalArgumentException(s"Sink ${nodeContext.ref} cannot be mocked")
      }
    }
  }
}