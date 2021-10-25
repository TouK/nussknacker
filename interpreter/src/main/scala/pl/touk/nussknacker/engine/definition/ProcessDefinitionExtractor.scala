package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, QueryableStateNames, SpelExpressionExcludeList}
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
      definition.signalsWithTransformers.values.map(_._1) ++
      definition.expressionConfig.globalVariables.values
    )(definition.settings) ++
      TypesInformation.extractFromClassList(definition.expressionConfig.additionalClasses)(definition.settings)
  }

  import pl.touk.nussknacker.engine.util.Implicits._

  // Returns object definitions with high-level possible return types of components within given ProcessConfigCreator.
  def extractObjectWithMethods(creator: ProcessConfigCreator,
                               processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {

    val componentsFromProviders = extractFromComponentProviders(creator.getClass.getClassLoader, processObjectDependencies)
    val services = creator.services(processObjectDependencies) ++ componentsFromProviders.services
    val sourceFactories = creator.sourceFactories(processObjectDependencies) ++ componentsFromProviders.sourceFactories
    val sinkFactories = creator.sinkFactories(processObjectDependencies) ++ componentsFromProviders.sinkFactories
    val customStreamTransformers = creator.customStreamTransformers(processObjectDependencies) ++ componentsFromProviders.customTransformers

    val signals = creator.signals(processObjectDependencies)

    val exceptionHandlerFactory = creator.exceptionHandlerFactory(processObjectDependencies)
    val expressionConfig = creator.expressionConfig(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)

    val servicesDefs = ObjectWithMethodDef.forMap(services, ProcessObjectDefinitionExtractor.service, componentsUiConfig)

    val customStreamTransformersDefs = ObjectWithMethodDef.forMap(customStreamTransformers, ProcessObjectDefinitionExtractor.customNodeExecutor, componentsUiConfig)

    val signalsDefs = ObjectWithMethodDef.forMap(signals, ProcessObjectDefinitionExtractor.signals, componentsUiConfig).map { case (signalName, signalSender) =>
      val transformers = customStreamTransformersDefs.filter { case (_, transformerDef) =>
        transformerDef.annotations.flatMap(_.cast[SignalTransformer]).exists(_.signalClass() == signalSender.obj.getClass)
      }.keySet
      (signalName, (signalSender, transformers))
    }

    val sourceFactoriesDefs = ObjectWithMethodDef.forMap(sourceFactories, ProcessObjectDefinitionExtractor.source, componentsUiConfig)


    val sinkFactoriesDefs = ObjectWithMethodDef.forMap(sinkFactories, ProcessObjectDefinitionExtractor.sink, componentsUiConfig)

    val exceptionHandlerFactoryDefs = ObjectWithMethodDef.withEmptyConfig(exceptionHandlerFactory, ProcessObjectDefinitionExtractor.exceptionHandler)

    val globalVariablesDefs = GlobalVariableDefinitionExtractor.extractDefinitions(expressionConfig.globalProcessVariables)

    val globalImportsDefs = expressionConfig.globalImports.map(_.value)

    val settings = creator.classExtractionSettings(processObjectDependencies)

    ProcessDefinition[ObjectWithMethodDef](
      servicesDefs, sourceFactoriesDefs,
      sinkFactoriesDefs,
      customStreamTransformersDefs.mapValuesNow(k => (k, extractCustomTransformerData(k))),
      signalsDefs, exceptionHandlerFactoryDefs, ExpressionDefinition(globalVariablesDefs,
        globalImportsDefs,
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
        expressionConfig.spelExpressionExcludeList
      ), settings)
  }

  def extractFromComponentProviders(classLoader: ClassLoader, processObjectDependencies: ProcessObjectDependencies): ComponentExtractor.ComponentsGroupedByType = {
    ComponentExtractor(classLoader).extractComponents(processObjectDependencies)
  }

  private def extractCustomTransformerData(objectWithMethodDef: ObjectWithMethodDef) = {
    val transformer = objectWithMethodDef.obj.asInstanceOf[CustomStreamTransformer]
    val queryNamesAnnotation = objectWithMethodDef.annotations.flatMap(_.cast[QueryableStateNames])
    val queryNames = queryNamesAnnotation.flatMap(_.values().toList).toSet
    CustomTransformerAdditionalData(queryNames, transformer.canHaveManyInputs, transformer.canBeEnding)
  }

  type TransformerId = String
  type QueryableStateName = String

  case class CustomTransformerAdditionalData(queryableStateNames: Set[QueryableStateName], manyInputs: Boolean, canBeEnding: Boolean)

  case class ProcessDefinition[T <: ObjectMetadata](services: Map[String, T],
                                                    sourceFactories: Map[String, T],
                                                    sinkFactories: Map[String, T],
                                                    //TODO: find easier way to handle *AdditionalData?
                                                    customStreamTransformers: Map[String, (T, CustomTransformerAdditionalData)],
                                                    signalsWithTransformers: Map[String, (T, Set[TransformerId])],
                                                    exceptionHandlerFactory: T,
                                                    expressionConfig: ExpressionDefinition[T],
                                                    settings: ClassExtractionSettings) {
    def componentIds: List[String] = {
      val ids = services.keys ++
        sourceFactories.keys ++
        sinkFactories.keys ++
        customStreamTransformers.keys ++
        signalsWithTransformers.keys
      ids.toList
    }
  }

  def toObjectDefinition(definition: ProcessDefinition[ObjectWithMethodDef]): ProcessDefinition[ObjectDefinition] = {
    val expressionDefinition = ExpressionDefinition(
      definition.expressionConfig.globalVariables.mapValuesNow(_.objectDefinition),
      definition.expressionConfig.globalImports,
      definition.expressionConfig.additionalClasses,
      definition.expressionConfig.languages,
      definition.expressionConfig.optimizeCompilation,
      definition.expressionConfig.strictTypeChecking,
      definition.expressionConfig.dictionaries,
      definition.expressionConfig.hideMetaVariable,
      definition.expressionConfig.strictMethodsChecking,
      definition.expressionConfig.staticMethodInvocationsChecking,
      definition.expressionConfig.methodExecutionForUnknownAllowed,
      definition.expressionConfig.dynamicPropertyAccessAllowed,
      definition.expressionConfig.spelExpressionExcludeList
    )
    ProcessDefinition(
      definition.services.mapValuesNow(_.objectDefinition),
      definition.sourceFactories.mapValuesNow(_.objectDefinition),
      definition.sinkFactories.mapValuesNow(_.objectDefinition),
      definition.customStreamTransformers.mapValuesNow { case (transformer, additionalData) => (transformer.objectDefinition, additionalData) },
      definition.signalsWithTransformers.mapValuesNow(sign => (sign._1.objectDefinition, sign._2)),
      definition.exceptionHandlerFactory.objectDefinition,
      expressionDefinition,
      definition.settings
    )
  }

  case class ExpressionDefinition[+T <: ObjectMetadata](globalVariables: Map[String, T], globalImports: List[String], additionalClasses: List[Class[_]],
                                                        languages: LanguageConfiguration, optimizeCompilation: Boolean, strictTypeChecking: Boolean,
                                                        dictionaries: Map[String, DictDefinition], hideMetaVariable: Boolean, strictMethodsChecking: Boolean,
                                                        staticMethodInvocationsChecking: Boolean, methodExecutionForUnknownAllowed: Boolean,
                                                        dynamicPropertyAccessAllowed: Boolean, spelExpressionExcludeList: SpelExpressionExcludeList)

}
