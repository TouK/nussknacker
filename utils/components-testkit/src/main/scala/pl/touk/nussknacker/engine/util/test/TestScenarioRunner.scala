package pl.touk.nussknacker.engine.util.test

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.process.ComponentUseCase.{EngineRuntime, TestRuntime}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.resultcollector.{ProductionServiceInvocationCollector, ResultCollector}
import pl.touk.nussknacker.engine.testmode.{
  ResultsCollectingListener,
  ResultsCollectingListenerHolder,
  TestServiceInvocationCollector
}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult

import scala.reflect.ClassTag

/**
  * This is entrypoint for all available test runners. Use:
  *
  * import SomeSpecificTestRunner._
  * TestScenarioRunner.specific()
  *
  * to run tests. For example for kafka-lite it will be:
  *
  * import LiteKafkaTestScenarioRunner._
  * TestScenarioRunner.kafkaLiteBased()
  *
  */
object TestScenarioRunner {

  type RunnerResult        = ValidatedNel[ProcessCompilationError, RunResult]
  type RunnerListResult[R] = ValidatedNel[ProcessCompilationError, RunListResult[R]]

  // TODO: Maybe we should replace ids with more meaningful: test-data, rest-result?
  val testDataSource = "source"
  val noopSource     = "noopSource"
  val testResultSink = "sink"

  def componentUseCase(testRuntimeMode: Boolean): ComponentUseCase = if (testRuntimeMode) TestRuntime else EngineRuntime
}

/**
  * This is *experimental* API
  *
  * This is common trait for all various engines - where each engine can have different entry method provided by convention runWith***Data.
  * For example see implementation on LiteKafkaTestScenarioRunner and LiteTestScenarioRunner
  */
trait TestScenarioRunner

trait TestScenarioRunnerBuilder[R <: TestScenarioRunner, B <: TestScenarioRunnerBuilder[R, _]] {

  def withExtraComponents(components: List[ComponentDefinition]): B

  def withExtraGlobalVariables(globalVariables: Map[String, AnyRef]): B

  def inTestRuntimeMode: B

  def build(): R

}

object TestScenarioCollectorHandler {

  def createHandler(componentUseCase: ComponentUseCase): TestScenarioCollectorHandler = {

    val resultsCollectingListener = ResultsCollectingListenerHolder.registerRun

    val resultCollector = if (ComponentUseCase.TestRuntime == componentUseCase) {
      new TestServiceInvocationCollector(resultsCollectingListener.runId)
    } else {
      ProductionServiceInvocationCollector
    }

    new TestScenarioCollectorHandler(resultCollector, resultsCollectingListener)
  }

  final class TestScenarioCollectorHandler(
      val resultCollector: ResultCollector,
      val resultsCollectingListener: ResultsCollectingListener
  ) extends AutoCloseable {
    def close(): Unit = resultsCollectingListener.close()
  }

}

trait ClassBasedTestScenarioRunner extends TestScenarioRunner {
  // TODO: add generate test data support
  def runWithData[T: ClassTag, R](scenario: CanonicalProcess, data: List[T]): RunnerListResult[R]
}

object RunResult {

  def success[T](data: T): RunListResult[T] =
    RunListResult(Nil, data :: Nil)

  def successes[T](data: List[T]): RunListResult[T] =
    RunListResult(Nil, data)

}

sealed trait RunResult {
  def errors: List[NuExceptionInfo[_]]
}

case class RunListResult[T](errors: List[NuExceptionInfo[_]], successes: List[T]) extends RunResult {

  def mapSuccesses[U](f: T => U): RunListResult[U] =
    copy(successes = successes.map(f))

}

case class RunUnitResult(errors: List[NuExceptionInfo[_]]) extends RunResult
