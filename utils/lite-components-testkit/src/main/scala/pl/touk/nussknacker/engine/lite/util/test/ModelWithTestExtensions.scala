package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.testing.LocalModelData

object ModelWithTestExtensions {

  def apply(
      config: Config,
      components: List[ComponentDefinition],
      extraGlobalVariables: Map[String, AnyRef]
  ): ModelData = {
    val configCreator = new DefaultConfigCreator {
      override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
        val extraVariablesWithCategories = extraGlobalVariables.map { case (key, value) =>
          key -> WithCategories.anyCategory(value)
        }
        val defaultExpressionConfig = super.expressionConfig(modelDependencies)
        defaultExpressionConfig.copy(
          globalProcessVariables = defaultExpressionConfig.globalProcessVariables ++ extraVariablesWithCategories
        )
      }
    }

    LocalModelData(config, components = components, configCreator)
  }

}
