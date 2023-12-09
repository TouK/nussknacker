package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
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

  def withExtensions[T](
      config: Config,
      components: List[ComponentDefinition],
      globalVariables: Map[String, AnyRef]
  )(action: ModelData => T): T = {
    val configCreator = new EmptyProcessConfigCreator {
      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
        val globalProcessVariables = globalVariables.map { case (key, value) =>
          key -> WithCategories.anyCategory(value)
        }
        ExpressionConfig(globalProcessVariables, List.empty)
      }
    }

    val model = LocalModelData(config, configCreator, components = components)
    action(model)
  }

}
