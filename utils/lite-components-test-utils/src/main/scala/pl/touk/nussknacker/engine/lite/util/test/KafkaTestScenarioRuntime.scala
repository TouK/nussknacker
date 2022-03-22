package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, WithCategories}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.MockComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRuntime

class KafkaTestScenarioRuntime(val components: Map[String, WithCategories[Component]], testConfig: Config) extends TestScenarioRuntime {

  override def run(scenario: EspProcess): Unit = {
    //model
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)
    val components = MockComponentsHolder.registerMockComponents(this.components)
    //todo implement
  }

  override val config: Config = this.testConfig

}
