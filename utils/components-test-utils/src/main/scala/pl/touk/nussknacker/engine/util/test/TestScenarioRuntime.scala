package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.graph.EspProcess

trait TestScenarioRuntime {
  val config: Config

  def run(scenario: EspProcess): Unit

  def produceData(dataGenerator: () => Any): Unit = {}

  def results(): Any = {}
}
