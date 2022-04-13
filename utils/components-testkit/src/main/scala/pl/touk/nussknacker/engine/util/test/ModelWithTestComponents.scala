package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{TestComponentsHolder, TestComponentsProvider}

object ModelWithTestComponents {

  //we add components by hand, which should be tested in scenario
  //components are registered in special ComponentProvider, which is configured with appropriate testRunId
  def prepareModelWithTestComponents(config: Config, components: List[ComponentDefinition]): ModelData = {
    val testComponentHolder = TestComponentsHolder.registerTestComponents(components)
    val configWithRunId = config.withValue(s"components.${TestComponentsProvider.name}.${TestComponentsProvider.testRunIdConfig}", fromAnyRef(testComponentHolder.runId.id))
    LocalModelData(configWithRunId, new EmptyProcessConfigCreator)
  }

}
