package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{ConversionsProvider, CustomStreamTransformer, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.component.{ComponentExtractor, ComponentsUiConfigExtractor}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._

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
                               classLoader: ClassLoader,
                               processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {

    val componentsFromProviders = extractFromComponentProviders(classLoader, processObjectDependencies)
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

  case class ModelDefinitionWithTypes(modelDefinition: ProcessDefinition[ObjectWithMethodDef]) {
    @transient lazy val typeDefinitions: TypeDefinitionSet = TypeDefinitionSet(ProcessDefinitionExtractor.extractTypes(modelDefinition))

    def filter(predicate: ObjectWithMethodDef => Boolean): ModelDefinitionWithTypes = {
      ModelDefinitionWithTypes(modelDefinition.filter(predicate))
    }

    def transform(f: ObjectWithMethodDef => ObjectWithMethodDef): ModelDefinitionWithTypes = {
      ModelDefinitionWithTypes(modelDefinition.transform(f))
    }
  }

  case class ComponentIdWithName(id: ComponentId, name: String)

  case class ProcessDefinition[T](services: Map[String,T],
                                  sourceFactories: Map[String, T],
                                  sinkFactories: Map[String, T],
                                  //TODO: find easier way to handle *AdditionalData?
                                  customStreamTransformers: Map[String, (T, CustomTransformerAdditionalData)],
                                  expressionConfig: ExpressionDefinition[T],
                                  settings: ClassExtractionSettings) {

    import pl.touk.nussknacker.engine.util.Implicits._

    val componentNames: List[String] = {
      val names = services.keys ++
        sourceFactories.keys ++
        sinkFactories.keys ++
        customStreamTransformers.keys

      names.toList
    }

    def servicesWithIds(componentIdProvider: ComponentIdProvider, processingType: String): Map[ComponentIdWithName, T] =
      services.map { case (name, obj) =>
        val hasNoReturn = obj match {
          case objDef: ObjectDefinition => objDef.returnType.isEmpty
          case objWithMethodDef: ObjectWithMethodDef => objWithMethodDef.returnType.isEmpty
        }

        val id = componentIdProvider.createComponentId(processingType, Some(name), if (hasNoReturn) ComponentType.Processor else ComponentType.Enricher)
        ComponentIdWithName(id, name) -> obj
      }

    def customStreamTransformersWithIds(componentIdProvider: ComponentIdProvider, processingType: String): Map[ComponentIdWithName, (T, CustomTransformerAdditionalData)] =
      customStreamTransformers.map { case (name, obj) =>
        val id = componentIdProvider.createComponentId(processingType, Some(name), ComponentType.CustomNode)
        ComponentIdWithName(id, name) -> obj
      }

    def sinkFactoriesWithIds(componentIdProvider: ComponentIdProvider, processingType: String): Map[ComponentIdWithName, T] =
      sinkFactories.map { case (name, obj) =>
        val id = componentIdProvider.createComponentId(processingType, Some(name), ComponentType.Sink)
        ComponentIdWithName(id, name) -> obj
      }

    def sourceFactoriesWithIds(componentIdProvider: ComponentIdProvider, processingType: String): Map[ComponentIdWithName, T] =
      sourceFactories.map { case (name, obj) =>
        val id = componentIdProvider.createComponentId(processingType, Some(name), ComponentType.Source)
        ComponentIdWithName(id, name) -> obj
      }

    def componentIdToName(componentIdProvider: ComponentIdProvider,
                          processingType: String
                         ): Map[ComponentId, String] = {
      (servicesWithIds(componentIdProvider, processingType) ++
        customStreamTransformersWithIds(componentIdProvider, processingType) ++
        sinkFactoriesWithIds(componentIdProvider, processingType) ++
        sourceFactoriesWithIds(componentIdProvider, processingType))
        .map { case (ComponentIdWithName(id, name), _) => id -> name }
    }

    def filter(predicate: T => Boolean): ProcessDefinition[T] = copy(
      services.filter(kv => predicate(kv._2)),
      sourceFactories.filter(kv => predicate(kv._2)),
      sinkFactories.filter(kv => predicate(kv._2)),
      customStreamTransformers.filter(ct => predicate(ct._2._1)),
      expressionConfig.copy(globalVariables = expressionConfig.globalVariables.filter(kv => predicate(kv._2))))

    def transform[R](f: T => R): ProcessDefinition[R] = copy(
      services.mapValuesNow(f),
      sourceFactories.mapValuesNow(f),
      sinkFactories.mapValuesNow(f),
      customStreamTransformers.mapValuesNow { case (o, additionalData) => (f(o), additionalData) },
      expressionConfig.copy(globalVariables = expressionConfig.globalVariables.mapValuesNow(f)))

  }

  case class ExpressionDefinition[T](globalVariables: Map[String, T], globalImports: List[String], additionalClasses: List[Class[_]],
                                     languages: LanguageConfiguration, optimizeCompilation: Boolean, strictTypeChecking: Boolean,
                                     dictionaries: Map[String, DictDefinition], hideMetaVariable: Boolean, strictMethodsChecking: Boolean,
                                     staticMethodInvocationsChecking: Boolean, methodExecutionForUnknownAllowed: Boolean,
                                     dynamicPropertyAccessAllowed: Boolean, spelExpressionExcludeList: SpelExpressionExcludeList,
                                     customConversionsProviders: List[ConversionsProvider])

}
