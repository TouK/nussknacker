package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{ConversionsProvider, CustomStreamTransformer, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.component.{ComponentExtractor, ComponentsUiConfigExtractor}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import shapeless.syntax.typeable._

object ProcessDefinitionExtractor {

  // Extracts details of types (e.g. field definitions for variable suggestions) of extracted objects definitions (see extractObjectWithMethods).
  // We don't do it inside extractObjectWithMethods because this is needed only on FE, and can be a bit costly
  def extractTypes(definition: ProcessDefinition[ObjectWithMethodDef]): Set[TypeInfos.ClazzDefinition] = {
    TypesInformation.extract(definition.services.values ++
      definition.sourceFactories.values ++
      definition.customStreamTransformers.values.map(_._1) ++
      definition.expressionConfig.globalVariables.values
    )(definition.settings) ++
      TypesInformation.extractFromClassList(definition.expressionConfig.additionalClasses)(definition.settings)
  }

  import pl.touk.nussknacker.engine.util.Implicits._

  // Returns object definitions with high-level possible return types of components within given ProcessConfigCreator.
  //TODO: enable passing components directly, without ComponentProvider discovery, e.g. for testing
  def extractObjectWithMethods(creator: ProcessConfigCreator,
                               processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {

    val componentsFromProviders = extractFromComponentProviders(creator.getClass.getClassLoader, processObjectDependencies)
    val services = creator.services(processObjectDependencies) ++ componentsFromProviders.services
    val sourceFactories = creator.sourceFactories(processObjectDependencies) ++ componentsFromProviders.sourceFactories
    val sinkFactories = creator.sinkFactories(processObjectDependencies) ++ componentsFromProviders.sinkFactories
    val customStreamTransformers = creator.customStreamTransformers(processObjectDependencies) ++ componentsFromProviders.customTransformers

    val expressionConfig = creator.expressionConfig(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)

    val servicesDefs = ObjectWithMethodDef.forMap(services, ProcessObjectDefinitionExtractor.service, componentsUiConfig)

    val customStreamTransformersDefs = ObjectWithMethodDef.forMap(customStreamTransformers, ProcessObjectDefinitionExtractor.customStreamTransformer, componentsUiConfig)

    val sourceFactoriesDefs = ObjectWithMethodDef.forMap(sourceFactories, ProcessObjectDefinitionExtractor.source, componentsUiConfig)

    val sinkFactoriesDefs = ObjectWithMethodDef.forMap(sinkFactories, ProcessObjectDefinitionExtractor.sink, componentsUiConfig)

    val settings = creator.classExtractionSettings(processObjectDependencies)

    ProcessDefinition[ObjectWithMethodDef](
      servicesDefs,
      sourceFactoriesDefs,
      sinkFactoriesDefs,
      customStreamTransformersDefs.mapValuesNow(k => (k, extractCustomTransformerData(k))),
      toExpressionDefinition(expressionConfig),
      settings)
  }

  private def toExpressionDefinition(expressionConfig: ExpressionConfig) =
    ExpressionDefinition(
      GlobalVariableDefinitionExtractor.extractDefinitions(expressionConfig.globalProcessVariables),
      expressionConfig.globalImports.map(_.value),
      expressionConfig.additionalClasses,
      expressionConfig.languages,
      expressionConfig.optimizeCompilation,
      expressionConfig.strictTypeChecking,
      expressionConfig.dictionaries.mapValuesNow(_.value),
      expressionConfig.hideMetaVariable,
      expressionConfig.strictMethodsChecking,
      expressionConfig.staticMethodInvocationsChecking,
      expressionConfig.methodExecutionForUnknownAllowed,
      expressionConfig.dynamicPropertyAccessAllowed,
      expressionConfig.spelExpressionExcludeList,
      expressionConfig.customConversionsProviders)

  def extractFromComponentProviders(classLoader: ClassLoader, processObjectDependencies: ProcessObjectDependencies): ComponentExtractor.ComponentsGroupedByType = {
    ComponentExtractor(classLoader).extractComponents(processObjectDependencies)
  }

  private def extractCustomTransformerData(objectWithMethodDef: ObjectWithMethodDef) = {
    val transformer = objectWithMethodDef.obj.asInstanceOf[CustomStreamTransformer]
    CustomTransformerAdditionalData(transformer.canHaveManyInputs, transformer.canBeEnding)
  }

  case class CustomTransformerAdditionalData(manyInputs: Boolean, canBeEnding: Boolean)

  case class ProcessDefinition[T <: ObjectMetadata](services: Map[String,T],
                                                    sourceFactories: Map[String, T],
                                                    sinkFactories: Map[String, T],
                                                    //TODO: find easier way to handle *AdditionalData?
                                                    customStreamTransformers: Map[String, (T, CustomTransformerAdditionalData)],
                                                    expressionConfig: ExpressionDefinition[T],
                                                    settings: ClassExtractionSettings) {

    def componentIds: List[String] = {
      val ids = services.keys ++
        sourceFactories.keys ++
        sinkFactories.keys ++
        customStreamTransformers.keys
      ids.toList
    }

    def forCategory(category: String): ProcessDefinition[T] = copy(
      services.filter(_._2.availableForCategory(category)),
      sourceFactories.filter(_._2.availableForCategory(category)),
      sinkFactories.filter(_._2.availableForCategory(category)),
      customStreamTransformers.filter(_._2._1.availableForCategory(category)),
      expressionConfig.copy(globalVariables = expressionConfig.globalVariables.filter(_._2.availableForCategory(category)))
    )
  }

  def toObjectDefinition(definition: ProcessDefinition[ObjectWithMethodDef]): ProcessDefinition[ObjectDefinition] = {
    val expressionConfig = definition.expressionConfig
    val expressionDefinition = toObjectExpressionDefinition(expressionConfig)
    ProcessDefinition(
      definition.services.mapValuesNow(_.objectDefinition),
      definition.sourceFactories.mapValuesNow(_.objectDefinition),
      definition.sinkFactories.mapValuesNow(_.objectDefinition),
      definition.customStreamTransformers.mapValuesNow { case (transformer, additionalData) => (transformer.objectDefinition, additionalData) },
      expressionDefinition,
      definition.settings
    )
  }

  private def toObjectExpressionDefinition(expressionConfig: ExpressionDefinition[ObjectWithMethodDef]): ExpressionDefinition[ObjectDefinition] =
    ExpressionDefinition(
      expressionConfig.globalVariables.mapValuesNow(_.objectDefinition),
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
      expressionConfig.customConversionsProviders)

  case class ExpressionDefinition[+T <: ObjectMetadata](globalVariables: Map[String, T], globalImports: List[String], additionalClasses: List[Class[_]],
                                                        languages: LanguageConfiguration, optimizeCompilation: Boolean, strictTypeChecking: Boolean,
                                                        dictionaries: Map[String, DictDefinition], hideMetaVariable: Boolean, strictMethodsChecking: Boolean,
                                                        staticMethodInvocationsChecking: Boolean, methodExecutionForUnknownAllowed: Boolean,
                                                        dynamicPropertyAccessAllowed: Boolean, spelExpressionExcludeList: SpelExpressionExcludeList,
                                                        customConversionsProviders: List[ConversionsProvider])

}
