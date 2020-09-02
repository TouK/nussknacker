package pl.touk.nussknacker.engine.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, QueryableStateNames}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}
import shapeless.syntax.typeable._
                 
object ProcessDefinitionExtractor {

  //we don't do it inside extractObjectWithMethods because this is needed only on FE, and can be a bit costly
  def extractTypes(definition: ProcessDefinition[ObjectWithMethodDef]): Set[TypeInfos.ClazzDefinition] = {
    TypesInformation.extract(definition.services.values ++
      definition.sourceFactories.values ++
      definition.customStreamTransformers.values.map(_._1) ++
      definition.signalsWithTransformers.values.map(_._1) ++
      definition.expressionConfig.globalVariables.values
    )(definition.settings)
  }

  import pl.touk.nussknacker.engine.util.Implicits._
  //TODO: move it to ProcessConfigCreator??
  def extractObjectWithMethods(creator: ProcessConfigCreator,
                               processObjectDependencies: ProcessObjectDependencies) : ProcessDefinition[ObjectWithMethodDef] = {

    val services = creator.services(processObjectDependencies)
    val signals = creator.signals(processObjectDependencies)
    val sourceFactories = creator.sourceFactories(processObjectDependencies)
    val sinkFactories = creator.sinkFactories(processObjectDependencies)
    val exceptionHandlerFactory = creator.exceptionHandlerFactory(processObjectDependencies)
    val customStreamTransformers = creator.customStreamTransformers(processObjectDependencies)
    val expressionConfig = creator.expressionConfig(processObjectDependencies)

    val nodesConfig = extractNodesConfig(processObjectDependencies.config)

    val servicesDefs = ObjectWithMethodDef.forMap(services, ProcessObjectDefinitionExtractor.service, nodesConfig)

    val customStreamTransformersDefs = ObjectWithMethodDef.forMap(customStreamTransformers, ProcessObjectDefinitionExtractor.customNodeExecutor, nodesConfig)

    val signalsDefs = ObjectWithMethodDef.forMap(signals, ProcessObjectDefinitionExtractor.signals, nodesConfig).map { case (signalName, signalSender) =>
      val transformers = customStreamTransformersDefs.filter { case (_, transformerDef) =>
          transformerDef.annotations.flatMap(_.cast[SignalTransformer]).exists(_.signalClass() == signalSender.obj.getClass)
      }.keySet
      (signalName, (signalSender, transformers))
    }

    val sourceFactoriesDefs = ObjectWithMethodDef.forMap(sourceFactories, ProcessObjectDefinitionExtractor.source, nodesConfig)


    val sinkFactoriesDefs = ObjectWithMethodDef.forMap(sinkFactories, ProcessObjectDefinitionExtractor.sink, nodesConfig)

    val exceptionHandlerFactoryDefs = ObjectWithMethodDef.withEmptyConfig(exceptionHandlerFactory, ProcessObjectDefinitionExtractor.exceptionHandler)

    val globalVariablesDefs = GlobalVariableDefinitionExtractor.extractDefinitions(expressionConfig.globalProcessVariables)

    val globalImportsDefs = expressionConfig.globalImports.map(_.value)

    val settings = creator.classExtractionSettings(processObjectDependencies)


    ProcessDefinition[ObjectWithMethodDef](
      servicesDefs, sourceFactoriesDefs,
      sinkFactoriesDefs.mapValuesNow(k => (k, extractSinkAdditionalData(k))),
      customStreamTransformersDefs.mapValuesNow(k => (k, extractCustomTransformerData(k))),
      signalsDefs, exceptionHandlerFactoryDefs, ExpressionDefinition(globalVariablesDefs,
        globalImportsDefs,
        expressionConfig.languages,
        expressionConfig.optimizeCompilation,
        expressionConfig.strictTypeChecking,
        expressionConfig.dictionaries.mapValuesNow(_.value),
        expressionConfig.hideMetaVariable,
        expressionConfig.strictMethodsChecking
      ), settings)
  }

  def extractNodesConfig(processConfig: Config) : Map[String, SingleNodeConfig] = {

    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._

    processConfig.getOrElse[Map[String, SingleNodeConfig]]("nodes", Map.empty)
  }

  private def extractSinkAdditionalData(objectWithMethodDef: ObjectWithMethodDef)  = {
    val sink = objectWithMethodDef.obj.asInstanceOf[SinkFactory]
    SinkAdditionalData(sink.requiresOutput)
  }

  private def extractCustomTransformerData(objectWithMethodDef: ObjectWithMethodDef) = {
    val transformer = objectWithMethodDef.obj.asInstanceOf[CustomStreamTransformer]
    val queryNamesAnnotation = objectWithMethodDef.annotations.flatMap(_.cast[QueryableStateNames])
    val queryNames = queryNamesAnnotation.flatMap(_.values().toList).toSet
    CustomTransformerAdditionalData(queryNames, transformer.clearsContext, transformer.canHaveManyInputs, transformer.canBeEnding)
  }

  type TransformerId = String
  type QueryableStateName = String

  case class CustomTransformerAdditionalData(queryableStateNames: Set[QueryableStateName], clearsContext: Boolean, manyInputs: Boolean, canBeEnding: Boolean)

  case class SinkAdditionalData(requiresOutput: Boolean)

  case class ProcessDefinition[T <: ObjectMetadata](services: Map[String, T],
                                                    sourceFactories: Map[String, T],
                                                   //TODO: find easier way to handle *AdditionalData?
                                                    sinkFactories: Map[String, (T, SinkAdditionalData)],
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

  def toObjectDefinition(definition: ProcessDefinition[ObjectWithMethodDef]) : ProcessDefinition[ObjectDefinition] = {
    val expressionDefinition = ExpressionDefinition(
      definition.expressionConfig.globalVariables.mapValuesNow(_.objectDefinition),
      definition.expressionConfig.globalImports,
      definition.expressionConfig.languages,
      definition.expressionConfig.optimizeCompilation,
      definition.expressionConfig.strictTypeChecking,
      definition.expressionConfig.dictionaries,
      definition.expressionConfig.hideMetaVariable,
      definition.expressionConfig.strictMethodsChecking
    )
    ProcessDefinition(
      definition.services.mapValuesNow(_.objectDefinition),
      definition.sourceFactories.mapValuesNow(_.objectDefinition),
      definition.sinkFactories.mapValuesNow { case (sink, additionalData) => (sink.objectDefinition, additionalData) },
      definition.customStreamTransformers.mapValuesNow { case (transformer, additionalData) => (transformer.objectDefinition, additionalData) },
      definition.signalsWithTransformers.mapValuesNow(sign => (sign._1.objectDefinition, sign._2)),
      definition.exceptionHandlerFactory.objectDefinition,
      expressionDefinition,
      definition.settings
    )
  }

  case class ExpressionDefinition[+T <: ObjectMetadata](globalVariables: Map[String, T], globalImports: List[String], languages: LanguageConfiguration,
                                                        optimizeCompilation: Boolean, strictTypeChecking: Boolean, dictionaries: Map[String, DictDefinition],
                                                        hideMetaVariable: Boolean, strictMethodsChecking: Boolean)

}
