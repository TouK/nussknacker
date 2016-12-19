package pl.touk.esp.engine.definition

import com.typesafe.config.Config
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, WithCategories}
import pl.touk.esp.engine.definition.DefinitionExtractor._

object ProcessDefinitionExtractor {

  import pl.touk.esp.engine.util.Implicits._
  //TODO: a moze to do ProcessConfigCreator??
  def extractObjectWithMethods(creator: ProcessConfigCreator, config: Config) : ProcessDefinition[ObjectWithMethodDef] = {

    val services = creator.services(config)
    val sourceFactories = creator.sourceFactories(config)
    val sinkFactories = creator.sinkFactories(config)
    val exceptionHandlerFactory = creator.exceptionHandlerFactory(config)
    val customStreamTransformers = creator.customStreamTransformers(config)
    val globalVariables = creator.globalProcessVariables(config)

    val servicesDefs = services.mapValuesNow { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.service)
    }

    val sourceFactoriesDefs = sourceFactories.mapValuesNow { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.source)
    }
    val sinkFactoriesDefs = sinkFactories.mapValuesNow { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.sink)
    }
    val exceptionHandlerFactoryDefs = ObjectWithMethodDef(
      WithCategories(exceptionHandlerFactory, List()), ProcessObjectDefinitionExtractor.exceptionHandler)
    val customNodesExecutorsDefs = customStreamTransformers.mapValuesNow { executor =>
      ObjectWithMethodDef(executor, ProcessObjectDefinitionExtractor.customNodeExecutor)
    }
    val globalVariablesDefs = globalVariables.mapValuesNow { globalVar =>
      globalVar.map(ClazzRef(_))
    }

    val typesInformation = TypesInformation.extract(servicesDefs.values,
      sourceFactoriesDefs.values,
      customNodesExecutorsDefs.values,
      globalVariables.values.map(_.value)
    )

    ProcessDefinition[ObjectWithMethodDef](
      servicesDefs, sourceFactoriesDefs, sinkFactoriesDefs,
      customNodesExecutorsDefs, exceptionHandlerFactoryDefs, globalVariablesDefs, typesInformation)
  }

  case class ProcessDefinition[T <: ObjectMetadata](services: Map[String, T],
                                                    sourceFactories: Map[String, T],
                                                    sinkFactories: Map[String, T],
                                                    customStreamTransformers: Map[String, T],
                                                    exceptionHandlerFactory: T,
                                                    globalVariables: Map[String, WithCategories[ClazzRef]],
                                                    typesInformation: List[PlainClazzDefinition]) {
  }

  object ObjectProcessDefinition {
    def empty: ProcessDefinition[ObjectDefinition] =
      ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty, ObjectDefinition.noParam, Map.empty, List.empty)

    def apply(definition: ProcessDefinition[ObjectWithMethodDef]) =
      ProcessDefinition(
        definition.services.mapValuesNow(_.objectDefinition),
        definition.sourceFactories.mapValuesNow(_.objectDefinition),
        definition.sinkFactories.mapValuesNow(_.objectDefinition),
        definition.customStreamTransformers.mapValuesNow(_.objectDefinition),
        definition.exceptionHandlerFactory.objectDefinition,
        definition.globalVariables,
        definition.typesInformation
      )
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

    def withCustomStreamTransformer(id: String, returnType: Class[_], params: Parameter*) =
      definition.copy(customStreamTransformers = definition.customStreamTransformers + (id -> ObjectDefinition(params.toList, returnType, List())))
  }
}

