package pl.touk.nussknacker.engine.util.test

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult

import scala.reflect.ClassTag

object TestScenarioRunner {
  type RunnerResult[R] = ValidatedNel[ProcessCompilationError, RunResult[R]]
}

/**
  * This is *experimental* API, currently it allows only for simple use case - synchronous invocation of test data
  *
  * This is common trait for all various engines - where each engine can have different entry method provided by convention runWith***Data.
  * For example see implementation on LiteKafkaTestScenarioRunner and LiteTestScenarioRunner
  */
trait TestScenarioRunner

trait ClassBasedTestScenarioRunner extends TestScenarioRunner {
  //todo add generate test data support
  def runWithData[T:ClassTag, R](scenario: CanonicalProcess, data: List[T]): RunnerResult[R]
}

object RunResult {

  def success[T](data: T): RunResult[T] =
    RunResult(Nil, data :: Nil)

  def successes[T](data: List[T]): RunResult[T] =
    RunResult(Nil, data)

  def errors[T](errors: List[NuExceptionInfo[_]]): RunResult[T] =
    RunResult[T](errors, List())

}

case class RunResult[T](errors: List[NuExceptionInfo[_]], successes: List[T]) {

  def mapSuccesses[U](f: T => U): RunResult[U] =
    copy(successes = successes.map(f))

}
