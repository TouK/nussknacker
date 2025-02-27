package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.{
  Component,
  ComponentAdditionalConfig,
  ComponentId,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.process.{
  ExpressionConfig,
  ProcessConfigCreator,
  ProcessObjectDependencies,
  WithCategories
}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionExtractor,
  ComponentDefinitionWithImplementation,
  Components
}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.globalvariables.{
  ExpressionConfigDefinition,
  GlobalVariableDefinitionWithImplementation
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

object ModelDefinitionFromConfigCreatorExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      categoryOpt: Option[String],
      modelDependencies: ProcessObjectDependencies,
      componentsUiConfig: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): ModelDefinition = {

    val sourceFactories          = creator.sourceFactories(modelDependencies).toList
    val sinkFactories            = creator.sinkFactories(modelDependencies).toList
    val services                 = creator.services(modelDependencies).toList
    val customStreamTransformers = creator.customStreamTransformers(modelDependencies).toList
    val allComponents            = sourceFactories ++ sinkFactories ++ services ++ customStreamTransformers

    val expressionConfig = creator.expressionConfig(modelDependencies)

    val components = extractFromComponentsList(
      allComponents,
      categoryOpt,
      componentsUiConfig,
      determineDesignerWideId,
      additionalConfigsFromProvider,
      componentDefinitionExtractionMode,
    )

    val settings = creator.classExtractionSettings(modelDependencies)

    ModelDefinition(
      components,
      toDefinition(expressionConfig, categoryOpt),
      settings
    )
  }

  private def extractFromComponentsList(
      components: List[(String, WithCategories[Component])],
      categoryOpt: Option[String],
      componentsUiConfig: ComponentsUiConfig,
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): Components = {
    Components.fold(
      componentDefinitionExtractionMode,
      collectAvailableForCategory(components, categoryOpt)
        .map { case (componentName, component, componentConfig) =>
          Components
            .withComponent(
              componentName,
              component,
              componentConfig,
              componentsUiConfig,
              determineDesignerWideId,
              additionalConfigsFromProvider,
              componentDefinitionExtractionMode
            )
        }
    )
  }

  private def toDefinition(
      expressionConfig: ExpressionConfig,
      categoryOpt: Option[String],
  ): ExpressionConfigDefinition = {
    val filteredVariables = collectAvailableForCategory(expressionConfig.globalProcessVariables.toList, categoryOpt)
    val variables = filteredVariables.map { case (name, variable, _) =>
      name -> GlobalVariableDefinitionWithImplementation(variable)
    }.toMap
    ExpressionConfigDefinition(
      variables,
      expressionConfig.globalImports,
      expressionConfig.additionalClasses,
      expressionConfig.optimizeCompilation,
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
