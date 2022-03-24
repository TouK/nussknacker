package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

class RequestResponseTestScenarioRunner(val components: Map[String, WithCategories[Component]], val config: Config) extends TestScenarioRunner {

  override def runWithData[T: ClassTag](scenario: EspProcess, data: List[T]): Unit = ???

  override def results(): Any = ???
}
