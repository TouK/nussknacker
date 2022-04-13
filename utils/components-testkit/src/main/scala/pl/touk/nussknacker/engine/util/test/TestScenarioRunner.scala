package pl.touk.nussknacker.engine.util.test

import pl.touk.nussknacker.engine.graph.EspProcess

import scala.reflect.ClassTag

/*
  This is *experimental* API, currently it allows only for simple use case - synchronous invocation of test data
  In the future, more testing methods will be added to allow more complex scenarios (e.g. asynchronous, waiting for condition before sending more test data, etc.)
 */
trait TestScenarioRunner {
  def runWithData[T: ClassTag, Result](scenario: EspProcess, data: List[T]): List[Result]
}
