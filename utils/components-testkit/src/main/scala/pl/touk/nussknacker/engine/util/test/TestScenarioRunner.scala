package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.graph.EspProcess

import scala.reflect.ClassTag

trait TestScenarioRunner {
  val config: Config

  def runWithData[T: ClassTag](scenario: EspProcess, data: List[T]): Unit

  def results(): Any
}
