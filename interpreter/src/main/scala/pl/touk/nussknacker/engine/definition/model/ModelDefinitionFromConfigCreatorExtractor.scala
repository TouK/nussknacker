package pl.touk.nussknacker.engine.definition.model

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{
  ExpressionConfig,
  ProcessConfigCreator,
  ProcessObjectDependencies,
  WithCategories
}
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionExtractor,
  ComponentDefinitionWithImplementation
}
import pl.touk.nussknacker.engine.definition.globalvariables.{
  ExpressionConfigDefinition,
  GlobalVariableDefinitionExtractor
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

object ModelDefinitionFromConfigCreatorExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      categoryOpt: Option[String],
      modelDependencies: ProcessObjectDependencies,
      componentsUiConfig: ComponentsUiConfig
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {

    val sourceFactories          = creator.sourceFactories(modelDependencies).toList
    val sinkFactories            = creator.sinkFactories(modelDependencies).toList
    val services                 = creator.services(modelDependencies).toList
    val customStreamTransformers = creator.customStreamTransformers(modelDependencies).toList
    val allComponents            = sourceFactories ++ sinkFactories ++ services ++ customStreamTransformers

    val expressionConfig = creator.expressionConfig(modelDependencies)

    val components = extractFromComponentsList(allComponents, categoryOpt, componentsUiConfig)

    val settings = creator.classExtractionSettings(modelDependencies)

    ModelDefinition[ComponentDefinitionWithImplementation](
      components,
      toDefinition(expressionConfig, categoryOpt),
      settings
    )
  }

  private def extractFromComponentsList(
      components: List[(String, WithCategories[Component])],
      categoryOpt: Option[String],
      componentsUiConfig: ComponentsUiConfig
  ): List[(String, ComponentDefinitionWithImplementation)] = {
    val componentsWithEnricherConfig = components.map { case (componentName, component) =>
      val config = componentsUiConfig.getConfigByComponentName(componentName) |+| component.componentConfig
      componentName -> component.withComponentConfig(config)
    }
    filterUsingComponentConfig(componentsWithEnricherConfig, categoryOpt).map { case (componentName, component) =>
      componentName -> ComponentDefinitionExtractor.extract(component)
    }
  }

  private def toDefinition(
      expressionConfig: ExpressionConfig,
      categoryOpt: Option[String],
  ): ExpressionConfigDefinition[ComponentDefinitionWithImplementation] = {
    val filteredVariables = filterUsingComponentConfig(expressionConfig.globalProcessVariables.toList, categoryOpt)
    val variables = filteredVariables.map { case (name, variable) =>
      name -> GlobalVariableDefinitionExtractor.extractDefinition(variable.value, variable.categories)
    }.toMap
    ExpressionConfigDefinition(
      variables,
      expressionConfig.globalImports,
      expressionConfig.additionalClasses,
      expressionConfig.languages,
      expressionConfig.optimizeCompilation,
      expressionConfig.strictTypeChecking,
      expressionConfig.dictionaries,
      expressionConfig.hideMetaVariable,
      expressionConfig.strictMethodsChecking,
      expressionConfig.staticMethodInvocationsChecking,
      expressionConfig.methodExecutionForUnknownAllowed,
      expressionConfig.dynamicPropertyAccessAllowed,
      expressionConfig.spelExpressionExcludeList,
      expressionConfig.customConversionsProviders
    )
  }

  private def filterUsingComponentConfig[T](list: List[(String, WithCategories[T])], categoryOpt: Option[String]) = {
    def availableForCategory(component: WithCategories[_]): Boolean =
      component.categories.isEmpty ||
        categoryOpt.forall(category => component.categories.exists(_.contains(category)))
    list.filter { case (_, withComponentConfig) =>
      !withComponentConfig.componentConfig.disabled && availableForCategory(withComponentConfig)
    }
  }

}
