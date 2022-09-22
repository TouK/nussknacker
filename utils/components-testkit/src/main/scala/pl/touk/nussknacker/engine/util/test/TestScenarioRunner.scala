package pl.touk.nussknacker.engine.util.test

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.{RunnerListResult, RunnerResult}

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

  type RunnerResult[R] = ValidatedNel[ProcessCompilationError, RunResult[R]]
  type RunnerListResult[R] = ValidatedNel[ProcessCompilationError, RunListResult[R]]

  // Maybe we should replace ids with more meaningful: test-data, rest-result?
  val testDataSource = "source"
  val noopSource = "noopSource"
  // Maybe we should unify those two test result nodes?
  val testResultSink = "sink"
  def testResultService: String = TestComponentsHolder.invocationCollectorComponentName

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

  def build(): R

}

trait ClassBasedTestScenarioRunner extends TestScenarioRunner {
  //todo add generate test data support
  def runWithData[T:ClassTag, R](scenario: CanonicalProcess, data: List[T]): RunnerListResult[R]
}

object RunResult {

  def success[T](data: T): RunListResult[T] =
    RunListResult(Nil, data :: Nil)

  def successes[T](data: List[T]): RunListResult[T] =
    RunListResult(Nil, data)

}

sealed trait RunResult[T] {
  def errors: List[NuExceptionInfo[_]]
  def successes: T
}

case class RunListResult[T](errors: List[NuExceptionInfo[_]], successes: List[T]) extends RunResult[List[T]] {

  def mapSuccesses[U](f: T => U): RunListResult[U] =
    copy(successes = successes.map(f))

}

case class RunUnitResult(errors: List[NuExceptionInfo[_]]) extends RunResult[Unit] {
  override def successes: Unit = ()
}
