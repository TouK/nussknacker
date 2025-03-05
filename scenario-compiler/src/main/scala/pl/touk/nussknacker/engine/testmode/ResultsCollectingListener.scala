package pl.touk.nussknacker.engine.testmode

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import io.circe.Json
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.util.Try

case class TestRunId private (id: String)

object TestRunId {
  def generate: TestRunId = new TestRunId(UUID.randomUUID().toString)

  def apply(id: String): TestRunId = throw new IllegalArgumentException("Please use generate instead of apply")
}

trait ResultsCollectingListener[T] extends ProcessListener with Serializable {

  def results: TestResults[T]

  def clean(): Unit

  private[testmode] def updateResults(action: TestResults[Any] => TestResults[Any]): Unit

  private[testmode] def variableEncoder: Any => T
}

private case class ResultsCollectingListenerImpl[T](holderClass: String, runId: TestRunId, variableEncoder: Any => T)
    extends ResultsCollectingListener[T] {

  override def results: TestResults[T] = ResultsCollectingListenerHolder.resultsForId(runId)

  // Warning! close can't clean resources because listener is passed into each scenario subpart and it will be closed few times
  // We have to use dedicated clean() method when we are sure that we consumed results instead.
  override final def close(): Unit = {}

  override def clean(): Unit = ResultsCollectingListenerHolder.cleanResult(runId)

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    updateResults(_.updateNodeResult(nodeId, context, variableEncoder))
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
    updateResults(_.updateExpressionResult(nodeId, context, expressionId, result, variableEncoder))
  }

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any]
  ): Unit = {}

  override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit =
    updateResults(_.updateExceptionResult(exceptionInfo, variableEncoder))

  private[testmode] override def updateResults(action: TestResults[Any] => TestResults[Any]): Unit = {
    ResultsCollectingListenerHolder.updateResults(runId, action)
  }

}

private object NoopResultsCollectingListener extends ResultsCollectingListener[Any] with EmptyProcessListener {
  override def results: TestResults[Any] = TestResults(Map.empty, Map.empty, Map.empty, List.empty)

  override def clean(): Unit = {}

  override private[testmode] def updateResults(action: TestResults[Any] => TestResults[Any]): Unit = {}

  override private[testmode] def variableEncoder: Any => Any = identity
}

object ResultsCollectingListenerHolder {

  private val results = new ConcurrentHashMap[TestRunId, TestResults[Any]]()

  private[testmode] def resultsForId[T](id: TestRunId): TestResults[T] = results.get(id).asInstanceOf[TestResults[T]]

  def withTestEngineListener[T](action: ResultsCollectingListener[Json] => T): T = {
    registerTestEngineListener.use(env => IO(action(env))).unsafeRunSync()
  }

  private[nussknacker] def registerTestEngineListener: Resource[IO, ResultsCollectingListener[Json]] = {
    Resource.make(IO(registerListener(TestInterpreterRunner.testResultsVariableEncoder)))(listener =>
      IO(listener.clean())
    )
  }

  def withListener[T](action: ResultsCollectingListener[Any] => T): T = {
    registerListener.use(env => IO(action(env))).unsafeRunSync()
  }

  private def registerListener: Resource[IO, ResultsCollectingListener[Any]] = {
    Resource.make(IO(registerListener(identity)))(listener => IO(listener.clean()))
  }

  val noopListener: ResultsCollectingListener[Any] = NoopResultsCollectingListener

  private[testmode] def cleanResult(runId: TestRunId): Unit = {
    results.remove(runId)
  }

  private def registerListener[T](variableEncoder: Any => T): ResultsCollectingListener[T] = {
    val runId = TestRunId.generate
    results.put(runId, TestResults(Map(), Map(), Map(), List()))
    ResultsCollectingListenerImpl(getClass.getCanonicalName, runId, variableEncoder)
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
