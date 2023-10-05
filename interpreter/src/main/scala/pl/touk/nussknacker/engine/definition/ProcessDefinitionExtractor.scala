package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{ConversionsProvider, CustomStreamTransformer, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.component.{ComponentExtractor, ComponentsUiConfigExtractor}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition.mapByName

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

  case class ComponentIdWithName(id: ComponentId, name: String)

  // Returns object definitions with high-level possible return types of components within given ProcessConfigCreator.
  //TODO: enable passing components directly, without ComponentProvider discovery, e.g. for testing
  def extractObjectWithMethods(creator: ProcessConfigCreator,
                               classLoader: ClassLoader,
                               processObjectDependencies: ProcessObjectDependencies,
                               processingType: String
                              ): ProcessDefinition[ObjectWithMethodDef] = {

    val componentsFromProviders = extractFromComponentProviders(classLoader, processObjectDependencies)
    val services = creator.services(processObjectDependencies) ++ componentsFromProviders.services
    val sourceFactories = creator.sourceFactories(processObjectDependencies) ++ componentsFromProviders.sourceFactories
    val sinkFactories = creator.sinkFactories(processObjectDependencies) ++ componentsFromProviders.sinkFactories
    val customStreamTransformers = creator.customStreamTransformers(processObjectDependencies) ++ componentsFromProviders.customTransformers

    val expressionConfig = creator.expressionConfig(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)

    val componentIdProvider: ComponentIdProvider = new DefaultComponentIdProvider(Map(processingType -> componentsUiConfig))

    val servicesDefs = ObjectWithMethodDef.forMap(services, ProcessObjectDefinitionExtractor.service, componentsUiConfig).map { case (name, objDef) =>
      val id: ComponentId = componentIdProvider.createComponentId(processingType, Some(name), if (objDef.returnType.isEmpty) ComponentType.Processor else ComponentType.Enricher)
      (ComponentIdWithName(id, name), objDef)
    }

    val customStreamTransformersDefs = ObjectWithMethodDef.forMap(customStreamTransformers, ProcessObjectDefinitionExtractor.customStreamTransformer, componentsUiConfig).map { case (name, objDef) =>
      val id: ComponentId = componentIdProvider.createComponentId(processingType, Some(name), ComponentType.CustomNode)
      (ComponentIdWithName(id, name), objDef)
    }

    val sourceFactoriesDefs = ObjectWithMethodDef.forMap(sourceFactories, ProcessObjectDefinitionExtractor.source, componentsUiConfig).map { case (name, objDef) =>
      val id: ComponentId = componentIdProvider.createComponentId(processingType, Some(name), ComponentType.Source)
      (ComponentIdWithName(id, name), objDef)
    }

    val sinkFactoriesDefs = ObjectWithMethodDef.forMap(sinkFactories, ProcessObjectDefinitionExtractor.sink, componentsUiConfig).map { case (name, objDef) =>
      val id: ComponentId = componentIdProvider.createComponentId(processingType, Some(name), ComponentType.Sink)
      (ComponentIdWithName(id, name), objDef)
    }

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

  object ProcessDefinition {
    def mapByName[T](mapByIdWithName: Map[ComponentIdWithName, T]): Map[String, T] = mapByIdWithName.map { case (idWithName, value) => idWithName.name -> value }
  }

  case class ProcessDefinition[T](services: Map[ComponentIdWithName,T],
                                  sourceFactories: Map[ComponentIdWithName, T],
                                  sinkFactories: Map[ComponentIdWithName, T],
                                  //TODO: find easier way to handle *AdditionalData?
                                  customStreamTransformers: Map[ComponentIdWithName, (T, CustomTransformerAdditionalData)],
                                  expressionConfig: ExpressionDefinition[T],
                                  settings: ClassExtractionSettings) {

    import pl.touk.nussknacker.engine.util.Implicits._
    val componentNames: List[String] = {
      val ids = services.keys ++
        sourceFactories.keys ++
        sinkFactories.keys ++
        customStreamTransformers.keys
      ids.map(_.name).toList
    }

    val componentIdToName: Map[ComponentId, String] = {
      (services.keys ++
        sourceFactories.keys ++
        sinkFactories.keys ++
        customStreamTransformers.keys)
        .map(c => c.id -> c.name).toMap
    }

    val servicesByName: Map[String, T] = mapByName(services)
    val sourceFactoriesByName: Map[String, T] = mapByName(sourceFactories)
    val sinkFactoriesByName: Map[String, T] = mapByName(sinkFactories)
    val customStreamTransformersByName: Map[String, (T, CustomTransformerAdditionalData)] = mapByName(customStreamTransformers)

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
