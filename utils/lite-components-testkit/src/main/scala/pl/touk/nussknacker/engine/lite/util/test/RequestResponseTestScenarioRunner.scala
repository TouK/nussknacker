package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

abstract class RequestResponseTestScenarioRunner(val components: Map[String, WithCategories[Component]], val config: Config) extends TestScenarioRunner {

}
