package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{
  ExpressionConfig,
  ProcessConfigCreator,
  ProcessObjectDependencies,
  WithCategories
}
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
    collectAvailableForCategory(components, categoryOpt).flatMap { case (componentName, component, componentConfig) =>
      ComponentDefinitionExtractor
        .extract(componentName, component, componentConfig, componentsUiConfig)
        .map(componentName -> _)
    }
  }

  private def toDefinition(
      expressionConfig: ExpressionConfig,
      categoryOpt: Option[String],
  ): ExpressionConfigDefinition[ComponentDefinitionWithImplementation] = {
    val filteredVariables = collectAvailableForCategory(expressionConfig.globalProcessVariables.toList, categoryOpt)
    val variables = filteredVariables.map { case (name, variable, _) =>
      name -> GlobalVariableDefinitionExtractor.extractDefinition(variable)
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

  private def collectAvailableForCategory[T](list: List[(String, WithCategories[T])], categoryOpt: Option[String]) = {
    def availableForCategory(component: WithCategories[_]): Boolean =
      component.categories.isEmpty ||
        categoryOpt.forall(category => component.categories.exists(_.contains(category)))
    list.collect {
      case (name, withComponentConfig) if availableForCategory(withComponentConfig) =>
        (name, withComponentConfig.value, withComponentConfig.componentConfig)
    }
  }

}
