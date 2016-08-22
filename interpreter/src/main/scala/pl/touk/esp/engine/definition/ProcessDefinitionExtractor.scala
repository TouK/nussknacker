package pl.touk.esp.engine.definition

import pl.touk.esp.engine.api.{FoldingFunction, Service}
import pl.touk.esp.engine.api.process.{SinkFactory, SourceFactory}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectDefinition, Parameter}

object ProcessDefinitionExtractor {

  def extract(objects: ProcessObjects) =
    ProcessDefinition(
      services = objects.services.mapValues(ServiceDefinitionExtractor.extract),
      sourceFactories = objects.sourceFactories.mapValues(SourceFactoryDefinitionExtractor.extract),
      sinkFactories = objects.sinkFactories.mapValues(SinkFactoryDefinitionExtractor.extract),
      foldingFunctions = objects.foldingFunctions.keySet
    )

  case class ProcessObjects(services: Map[String, Service],
                            sourceFactories: Map[String, SourceFactory[_]],
                            sinkFactories: Map[String, SinkFactory],
                            foldingFunctions: Map[String, FoldingFunction[_]])

  case class ProcessDefinition(services: Map[String, ObjectDefinition],
                               sourceFactories: Map[String, ObjectDefinition],
                               sinkFactories: Map[String, ObjectDefinition],
                               foldingFunctions: Set[String]) {

    def withService(id: String, params: Parameter*) =
      copy(services = services + (id -> ObjectDefinition(params.toList)))

    def withSourceFactory(typ: String, params: Parameter*) =
      copy(sourceFactories = sourceFactories + (typ -> ObjectDefinition(params.toList)))

    def withSinkFactory(typ: String, params: Parameter*) =
      copy(sinkFactories = sinkFactories + (typ -> ObjectDefinition(params.toList)))

    def withFoldingFunction(name: String) =
      copy(foldingFunctions = foldingFunctions + name)

  }

  object ProcessDefinition {
    def empty: ProcessDefinition = ProcessDefinition(Map.empty, Map.empty, Map.empty, Set.empty)
  }


}

