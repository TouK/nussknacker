package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{
  EmptyProcessConfigCreator,
  ExpressionConfig,
  ProcessObjectDependencies,
  WithCategories
}
import pl.touk.nussknacker.engine.testing.LocalModelData

object ModelWithTestExtensions {

  // we add components by hand, which should be tested in scenario
  // components are registered in special ComponentProvider, which is configured with appropriate testRunId
  def withExtensions[T](
      config: Config,
      components: List[ComponentDefinition],
      globalVariables: Map[String, AnyRef]
  )(action: ModelData => T): T = {
    val testExtensionsHolder = TestExtensionsHolder.registerTestExtensions(components, globalVariables)
    val configWithRunId = config.withValue(
      s"components.${TestComponentsProvider.name}.${TestComponentsProvider.testRunIdConfig}",
      fromAnyRef(testExtensionsHolder.runId.id)
    )

    val configCreator = new EmptyProcessConfigCreator {
      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies) = {
        val globalProcessVariables = globalVariables.map { case (key, value) =>
          key -> WithCategories.anyCategory(value)
        }

        ExpressionConfig(globalProcessVariables, List.empty)
      }
    }

    val model = LocalModelData(configWithRunId, configCreator)
    try {
      action(model)
    } finally {
      testExtensionsHolder.clean()
    }
  }

}
