package pl.touk.nussknacker.engine.util.test

import pl.touk.nussknacker.engine.graph.EspProcess

import scala.reflect.ClassTag

trait TestScenarioRunner {
  def runWithData[T: ClassTag](scenario: EspProcess, data: List[T]): Unit

  def results(): Any
}
