package pl.touk.esp.engine.definition

import com.typesafe.config.Config
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.{CustomStreamTransformer, FoldingFunction, Service}
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectDefinition, Parameter, PlainClazzDefinition, TypesInformation}
import pl.touk.esp.engine.types.EspTypeUtils

object ProcessDefinitionExtractor {

  def extract(objects: ProcessObjects) = {
    ProcessDefinition(
      services = objects.services.mapValues(ServiceDefinitionExtractor.extract),
      sourceFactories = objects.sourceFactories.mapValues(ProcessObjectDefinitionExtractor.source.extract),
      sinkFactories = objects.sinkFactories.mapValues(ProcessObjectDefinitionExtractor.sink.extract),
      foldingFunctions = objects.foldingFunctions.keySet,
      customStreamTransformers =  objects.customStreamTransformers.mapValues(ProcessObjectDefinitionExtractor.customNodeExecutor.extract),
      exceptionHandlerFactory = ProcessObjectDefinitionExtractor.exceptionHandler.extract(objects.exceptionHandlerFactory),
      typesInformation = TypesInformation.extract(objects.services, objects.sourceFactories, objects.sinkFactories)
    )
  }

  case class ProcessObjects(services: Map[String, Service],
                            sourceFactories: Map[String, SourceFactory[_]],
                            sinkFactories: Map[String, SinkFactory],
                            foldingFunctions: Map[String, FoldingFunction[_]],
                            customStreamTransformers: Map[String, CustomStreamTransformer],
                            exceptionHandlerFactory: ExceptionHandlerFactory)

  object ProcessObjects {
    def apply(creator: ProcessConfigCreator, config: Config): ProcessObjects = {
      ProcessObjects(
        services = creator.services(config),
        sourceFactories = creator.sourceFactories(config),
        sinkFactories = creator.sinkFactories(config),
        foldingFunctions = creator.foldingFunctions(config),
        customStreamTransformers = creator.customStreamTransformers(config),

        exceptionHandlerFactory = creator.exceptionHandlerFactory(config)
      )
    }
  }

  case class ProcessDefinition(services: Map[String, ObjectDefinition],
                               sourceFactories: Map[String, ObjectDefinition],
                               sinkFactories: Map[String, ObjectDefinition],
                               foldingFunctions: Set[String],
                               customStreamTransformers: Map[String, ObjectDefinition],
                               exceptionHandlerFactory: ObjectDefinition,
                               typesInformation: List[PlainClazzDefinition]) {

    def withService(id: String, params: Parameter*) =
      copy(services = services + (id -> ObjectDefinition.withParams(params.toList)))

    def withSourceFactory(typ: String, params: Parameter*) =
      copy(sourceFactories = sourceFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withSinkFactory(typ: String, params: Parameter*) =
      copy(sinkFactories = sinkFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withFoldingFunction(name: String) =
      copy(foldingFunctions = foldingFunctions + name)

    def withExceptionHandlerFactory(params: Parameter*) =
      copy(exceptionHandlerFactory = ObjectDefinition.withParams(params.toList))

    def withCustomStreamTransformer(id: String, params: Parameter*) =
      copy(customStreamTransformers = customStreamTransformers + (id -> ObjectDefinition.withParams(params.toList)))

  }

  object ProcessDefinition {
    def empty: ProcessDefinition = ProcessDefinition(Map.empty, Map.empty, Map.empty, Set.empty, Map.empty, ObjectDefinition.noParam, List.empty)
  }

}

