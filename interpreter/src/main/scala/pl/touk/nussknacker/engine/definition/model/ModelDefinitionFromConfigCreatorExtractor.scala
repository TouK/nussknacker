package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.{ExpressionDefinition, GlobalVariableDefinitionExtractor}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

object ModelDefinitionFromConfigCreatorExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      // Category is only for components extracted from ProcessConfigCreator purpose, it will be removed eventually
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

    val components = ComponentDefinitionWithImplementation.forListWithCategories(allComponents, componentsUiConfig)

    val settings = creator.classExtractionSettings(modelDependencies)

    val modelDefinition = ModelDefinition[ComponentDefinitionWithImplementation](
      components,
      toExpressionDefinition(expressionConfig),
      settings
    )
    categoryOpt
      .map(c => modelDefinition.filter(_.availableForCategory(c)))
      .getOrElse(modelDefinition)
  }

  private def toExpressionDefinition(expressionConfig: ExpressionConfig) =
    ExpressionDefinition(
      GlobalVariableDefinitionExtractor.extractDefinitions(expressionConfig.globalProcessVariables),
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
