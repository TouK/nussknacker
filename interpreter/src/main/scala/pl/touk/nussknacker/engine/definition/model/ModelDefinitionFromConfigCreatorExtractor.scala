package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.{ExpressionDefinition, GlobalVariableDefinitionExtractor}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser

object ModelDefinitionFromConfigCreatorExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      processObjectDependencies: ProcessObjectDependencies,
      categoryOpt: Option[String]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {

    val sourceFactories          = creator.sourceFactories(processObjectDependencies).toList
    val sinkFactories            = creator.sinkFactories(processObjectDependencies).toList
    val services                 = creator.services(processObjectDependencies).toList
    val customStreamTransformers = creator.customStreamTransformers(processObjectDependencies).toList
    val allComponents            = sourceFactories ++ sinkFactories ++ services ++ customStreamTransformers

    val expressionConfig = creator.expressionConfig(processObjectDependencies)

    val componentsUiConfig = ComponentsUiConfigParser.parse(processObjectDependencies.config)
    val components         = ComponentDefinitionWithImplementation.forList(allComponents, componentsUiConfig)

    val settings = creator.classExtractionSettings(processObjectDependencies)

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
