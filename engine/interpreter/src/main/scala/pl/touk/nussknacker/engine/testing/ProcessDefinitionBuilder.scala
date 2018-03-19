package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef, Parameter}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedParameters}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.util.Implicits._

import scala.concurrent.Future

object ProcessDefinitionBuilder {

  def empty: ProcessDefinition[ObjectDefinition] =
    ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, ObjectDefinition.noParam,
      ExpressionDefinition(Map.empty, List.empty, optimizeCompilation = true), List.empty)

  def withEmptyObjects(definition: ProcessDefinition[ObjectDefinition]): ProcessDefinition[ObjectWithMethodDef] = {

    def makeDummyDefinition(objectDefinition: ObjectDefinition, realType: ClazzRef = ClazzRef[Any]) = new ObjectWithMethodDef(null,
      MethodDefinition("", (_, _) => null, new OrderedParameters(objectDefinition.parameters.map(Left(_))),
        ClazzRef[Any],
        realType, List()), objectDefinition)

    val expressionDefinition = ExpressionDefinition(
      definition.expressionConfig.globalVariables.mapValuesNow(makeDummyDefinition(_)),
      definition.expressionConfig.globalImports,
      definition.expressionConfig.optimizeCompilation
    )

    ProcessDefinition(
      definition.services.mapValuesNow(makeDummyDefinition(_, ClazzRef[Future[_]])),
      definition.sourceFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.sinkFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.customStreamTransformers.mapValuesNow { case (transformer, queryNames) => (makeDummyDefinition(transformer), queryNames) },
      definition.signalsWithTransformers.mapValuesNow(sign => (makeDummyDefinition(sign._1), sign._2)),
      makeDummyDefinition(definition.exceptionHandlerFactory),
      expressionDefinition,
      definition.typesInformation
    )
  }

  implicit class ObjectProcessDefinition(definition: ProcessDefinition[ObjectDefinition]) {
    def withService(id: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> ObjectDefinition.withParams(params.toList)))

    def withSourceFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories = definition.sourceFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withSinkFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sinkFactories = definition.sinkFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withExceptionHandlerFactory(params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(exceptionHandlerFactory = ObjectDefinition.withParams(params.toList))

    def withCustomStreamTransformer(id: String, returnType: Class[_], additionalData: CustomTransformerAdditionalData, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (id -> (ObjectDefinition(params.toList, ClazzRef(returnType), List()), additionalData)))

    def withSignalsWithTransformers(id: String, returnType: Class[_], transformers: Set[String], params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(signalsWithTransformers = definition.signalsWithTransformers + (id -> (ObjectDefinition(params.toList, ClazzRef(returnType), List()), transformers)))

  }

}
