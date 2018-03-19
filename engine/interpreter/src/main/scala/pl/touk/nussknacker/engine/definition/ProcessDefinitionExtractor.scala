package pl.touk.nussknacker.engine.definition

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, WithCategories}
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, QueryableStateNames}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedParameters}
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import shapeless.syntax.typeable._

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
          transformerDef.methodDef.annotations.flatMap(_.cast[SignalTransformer]).exists(_.signalClass() == signal.value.getClass)
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
    val globalVariablesDefs = expressionConfig.globalProcessVariables.map { case (varName, globalVar) =>
      val klass = ClazzRef(globalVar.value.getClass)
      (varName, ObjectWithMethodDef(globalVar.value, MethodDefinition(varName, (_, _) => globalVar, new OrderedParameters(List()), klass,  klass, List()),
        ObjectDefinition(List(), klass, globalVar.categories)))
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
      signalsDefs, exceptionHandlerFactoryDefs, ExpressionDefinition(globalVariablesDefs, globalImportsDefs, expressionConfig.optimizeCompilation), typesInformation)
  }
  
  private def extractCustomTransformerData(objectWithMethodDef: ObjectWithMethodDef) = {
    val transformer = objectWithMethodDef.obj.asInstanceOf[CustomStreamTransformer]
    val queryNamesAnnotation = objectWithMethodDef.methodDef.annotations.flatMap(_.cast[QueryableStateNames])
    val queryNames = queryNamesAnnotation.flatMap(_.values().toList).toSet
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
                                                    typesInformation: List[ClazzDefinition]) {
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
      definition.expressionConfig.optimizeCompilation
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

  case class ExpressionDefinition[+T <: ObjectMetadata](globalVariables: Map[String, T], globalImports: List[String], optimizeCompilation: Boolean)

}