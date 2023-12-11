package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentFromProvidersExtractor
}
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodDefinitionExtractor
import pl.touk.nussknacker.engine.definition.globalvariables.{ExpressionDefinition, GlobalVariableDefinitionExtractor}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser

object ModelDefinitionExtractor {

  import pl.touk.nussknacker.engine.util.Implicits._

  // Returns object definitions with high-level possible return types of components within given ProcessConfigCreator.
  // TODO: enable passing components directly, without ComponentProvider discovery, e.g. for testing
  def extractModelDefinition(
      creator: ProcessConfigCreator,
      classLoader: ClassLoader,
      processObjectDependencies: ProcessObjectDependencies,
      // It won't be needed to pass category after we get rid of ProcessConfigCreator API
      category: Option[String]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {

    val componentsFromProviders = extractFromComponentProviders(classLoader, processObjectDependencies)
    val services                = creator.services(processObjectDependencies) ++ componentsFromProviders.services
    val sourceFactories = creator.sourceFactories(processObjectDependencies) ++ componentsFromProviders.sourceFactories
    val sinkFactories   = creator.sinkFactories(processObjectDependencies) ++ componentsFromProviders.sinkFactories
    val customStreamTransformers =
      creator.customStreamTransformers(processObjectDependencies) ++ componentsFromProviders.customTransformers

    val expressionConfig   = creator.expressionConfig(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigParser.parse(processObjectDependencies.config)

    val servicesDefs =
      ComponentDefinitionWithImplementation.forMap(services, MethodDefinitionExtractor.Service, componentsUiConfig)

    val customStreamTransformersDefs = ComponentDefinitionWithImplementation.forMap(
      customStreamTransformers,
      MethodDefinitionExtractor.CustomStreamTransformer,
      componentsUiConfig
    )

    val sourceFactoriesDefs =
      ComponentDefinitionWithImplementation.forMap(
        sourceFactories,
        MethodDefinitionExtractor.Source,
        componentsUiConfig
      )

    val sinkFactoriesDefs =
      ComponentDefinitionWithImplementation.forMap(sinkFactories, MethodDefinitionExtractor.Sink, componentsUiConfig)

    val settings = creator.classExtractionSettings(processObjectDependencies)

    val definition = ModelDefinition[ComponentDefinitionWithImplementation](
      servicesDefs,
      sourceFactoriesDefs,
      sinkFactoriesDefs,
      customStreamTransformersDefs.mapValuesNow(k => (k, extractCustomTransformerData(k))),
      toExpressionDefinition(expressionConfig),
      settings
    )

    category
      .map(c => definition.filter(_.availableForCategory(c)))
      .getOrElse(definition)
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

  private def extractFromComponentProviders(
      classLoader: ClassLoader,
      processObjectDependencies: ProcessObjectDependencies
  ): ComponentFromProvidersExtractor.ComponentsGroupedByType = {
    ComponentFromProvidersExtractor(classLoader).extractComponents(processObjectDependencies)
  }

  private def extractCustomTransformerData(compomentWithImpl: ComponentDefinitionWithImplementation) = {
    val transformer = compomentWithImpl.implementation.asInstanceOf[CustomStreamTransformer]
    CustomTransformerAdditionalData(transformer.canHaveManyInputs, transformer.canBeEnding)
  }

}
