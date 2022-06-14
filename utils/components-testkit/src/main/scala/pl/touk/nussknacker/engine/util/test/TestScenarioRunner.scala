package pl.touk.nussknacker.engine.util.test

import pl.touk.nussknacker.engine.graph.EspProcess

import scala.reflect.ClassTag

/**
  * This is *experimental* API, currently it allows only for simple use case - synchronous invocation of test data
  *
  * This is common trait for all various engines - where each engine can have different entry method provided by convention runWith***Data.
  * For example see implementation on LiteKafkaTestScenarioRunner and LiteTestScenarioRunner
  */
trait TestScenarioRunner

trait ClassBaseTestScenarioRunner extends TestScenarioRunner {
  def runWithData[T:ClassTag, R](scenario: EspProcess, data: List[T]): List[R]
}
