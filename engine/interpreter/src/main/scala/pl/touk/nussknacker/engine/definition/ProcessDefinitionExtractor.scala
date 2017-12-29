package pl.touk.nussknacker.engine.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, WithCategories}
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, QueryableStateNames}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedParameters}

object ProcessDefinitionExtractor {

  import pl.touk.nussknacker.engine.util.Implicits._
  //TODO: move it to ProcessConfigCreator??
  def extractObjectWithMethods(creator: ProcessConfigCreator, config: Config) : ProcessDefinition[ObjectWithMethodDef] = {

    val services = creator.services(config)
    val signals = creator.signals(config)
    val sourceFactories = creator.sourceFactories(config)
    val sinkFactories = creator.sinkFactories(config)
    val exceptionHandlerFactory = creator.exceptionHandlerFactory(config)
    val customStreamTransformers = creator.customStreamTransformers(config)
    val expressionConfig = creator.expressionConfig(config)

    val servicesDefs = services.mapValuesNow { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.service)
    }

    val customStreamTransformersDefs = customStreamTransformers.mapValuesNow { executor =>
      ObjectWithMethodDef(executor, ProcessObjectDefinitionExtractor.customNodeExecutor)
    }

    val signalsDefs = signals.map { case (signalName, signal) =>
      val signalSender = ObjectWithMethodDef(signal, ProcessObjectDefinitionExtractor.signals)
      val transformers = customStreamTransformersDefs.filter { case (_, (transformerDef)) =>
          Option(transformerDef.methodDef.method.getAnnotation(classOf[SignalTransformer])).exists(_.signalClass() == signal.value.getClass)
      }.keySet
      (signalName, (signalSender, transformers))
    }

    val sourceFactoriesDefs = sourceFactories.mapValuesNow { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.source)
    }
    val sinkFactoriesDefs = sinkFactories.mapValuesNow { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.sink)
    }
    val exceptionHandlerFactoryDefs = ObjectWithMethodDef(
      WithCategories(exceptionHandlerFactory, List()), ProcessObjectDefinitionExtractor.exceptionHandler)

    //TODO: this is not so nice...
    val globalVariablesDefs = expressionConfig.globalProcessVariables.mapValuesNow { globalVar =>
      val klass = globalVar.value.getClass
      ObjectWithMethodDef(globalVar.value, MethodDefinition(null, klass, new OrderedParameters(List())),
        ObjectDefinition(List(), klass, globalVar.categories))
    }

    val globalImportsDefs = expressionConfig.globalImports.map(_.value)

    val typesInformation = TypesInformation.extract(servicesDefs.values,
      sourceFactoriesDefs.values,
      customStreamTransformersDefs.values,
      signalsDefs.values.map(_._1),
      globalVariablesDefs.values.map(_.methodDef.returnType)
    )(creator.classExtractionSettings(config))

    ProcessDefinition[ObjectWithMethodDef](
      servicesDefs, sourceFactoriesDefs, sinkFactoriesDefs,
      customStreamTransformersDefs.mapValuesNow(k => (k, extractCustomTransformerData(k))),
      signalsDefs, exceptionHandlerFactoryDefs, ExpressionDefinition(globalVariablesDefs, globalImportsDefs), typesInformation)
  }
  
  private def extractCustomTransformerData(objectWithMethodDef: ObjectWithMethodDef) = {
    val transformer = objectWithMethodDef.obj.asInstanceOf[CustomStreamTransformer]
    val queryNamesAnnotation = objectWithMethodDef.methodDef.method.getAnnotation(classOf[QueryableStateNames])
    val queryNames = Option(queryNamesAnnotation).toList.flatMap(_.values().toList).toSet
    CustomTransformerAdditionalData(queryNames, transformer.clearsContext)
  }

  type TransformerId = String
  type QueryableStateName = String

  case class CustomTransformerAdditionalData(queryableStateNames: Set[QueryableStateName], clearsContext: Boolean)

  case class ProcessDefinition[T <: ObjectMetadata](services: Map[String, T],
                                                    sourceFactories: Map[String, T],
                                                    sinkFactories: Map[String, T],
                                                    customStreamTransformers: Map[String, (T, CustomTransformerAdditionalData)],
                                                    signalsWithTransformers: Map[String, (T, Set[TransformerId])],
                                                    exceptionHandlerFactory: T,
                                                    expressionConfig: ExpressionDefinition[T],
                                                    typesInformation: List[PlainClazzDefinition]) {
    def componentIds: List[String] = {
      val ids = services.keys ++
        sourceFactories.keys ++
        sinkFactories.keys ++
        customStreamTransformers.keys ++
        signalsWithTransformers.keys
      ids.toList
    }

  }

  object ObjectProcessDefinition {
    def empty: ProcessDefinition[ObjectDefinition] =
      ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, ObjectDefinition.noParam,
        ExpressionDefinition(Map.empty, List.empty), List.empty)

    def apply(definition: ProcessDefinition[ObjectWithMethodDef]) : ProcessDefinition[ObjectDefinition] = {
      val expressionDefinition = ExpressionDefinition(
        definition.expressionConfig.globalVariables.mapValuesNow(_.objectDefinition),
        definition.expressionConfig.globalImports
      )
      ProcessDefinition(
        definition.services.mapValuesNow(_.objectDefinition),
        definition.sourceFactories.mapValuesNow(_.objectDefinition),
        definition.sinkFactories.mapValuesNow(_.objectDefinition),
        definition.customStreamTransformers.mapValuesNow { case (transformer, queryNames) => (transformer.objectDefinition, queryNames) },
        definition.signalsWithTransformers.mapValuesNow(sign => (sign._1.objectDefinition, sign._2)),
        definition.exceptionHandlerFactory.objectDefinition,
        expressionDefinition,
        definition.typesInformation
      )
    }
  }

  implicit class ObjectProcessDefinition(definition: ProcessDefinition[ObjectDefinition]) {
    def withService(id: String, params: Parameter*) =
      definition.copy(services = definition.services + (id -> ObjectDefinition.withParams(params.toList)))

    def withSourceFactory(typ: String, params: Parameter*) =
      definition.copy(sourceFactories = definition.sourceFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withSinkFactory(typ: String, params: Parameter*) =
      definition.copy(sinkFactories = definition.sinkFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withExceptionHandlerFactory(params: Parameter*) =
      definition.copy(exceptionHandlerFactory = ObjectDefinition.withParams(params.toList))

    def withCustomStreamTransformer(id: String, returnType: Class[_], additionalData: CustomTransformerAdditionalData, params: Parameter*) =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (id -> (ObjectDefinition(params.toList, returnType, List()), additionalData)))

    def withSignalsWithTransformers(id: String, returnType: Class[_], transformers: Set[String], params: Parameter*) =
      definition.copy(signalsWithTransformers = definition.signalsWithTransformers + (id -> (ObjectDefinition(params.toList, returnType, List()), transformers)))

  }

  case class ExpressionDefinition[+T <: ObjectMetadata](globalVariables: Map[String, T], globalImports: List[String])

}