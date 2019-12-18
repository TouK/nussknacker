package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition, SinkAdditionalData}
import pl.touk.nussknacker.engine.util.Implicits._

import scala.concurrent.Future

object ProcessDefinitionBuilder {

  def empty: ProcessDefinition[ObjectDefinition] =
    ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, ObjectDefinition.noParam,
      ExpressionDefinition(Map.empty, List.empty, languages = LanguageConfiguration(List.empty),
        optimizeCompilation = true, strictTypeChecking = true, dictionaries = Map.empty, hideMetaVariable = false), Set.empty)

  def withEmptyObjects(definition: ProcessDefinition[ObjectDefinition]): ProcessDefinition[ObjectWithMethodDef] = {

    def makeDummyDefinition(objectDefinition: ObjectDefinition, realType: TypingResult = Typed[Any]) = new ObjectWithMethodDef(null,
      MethodDefinition("", (_, _) => null, new OrderedDependencies(objectDefinition.parameters),
        Typed[Any],
        realType, List()), objectDefinition)

    val expressionDefinition = ExpressionDefinition(
      definition.expressionConfig.globalVariables.mapValuesNow(makeDummyDefinition(_)),
      definition.expressionConfig.globalImports,
      definition.expressionConfig.languages,
      definition.expressionConfig.optimizeCompilation,
      definition.expressionConfig.strictTypeChecking,
      definition.expressionConfig.dictionaries,
      definition.expressionConfig.hideMetaVariable
    )

    ProcessDefinition(
      definition.services.mapValuesNow(makeDummyDefinition(_, Typed[Future[_]])),
      definition.sourceFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.sinkFactories.mapValuesNow { case (sink, additional) => (makeDummyDefinition(sink), additional) },
      definition.customStreamTransformers.mapValuesNow { case (transformer, queryNames) => (makeDummyDefinition(transformer), queryNames) },
      definition.signalsWithTransformers.mapValuesNow(sign => (makeDummyDefinition(sign._1), sign._2)),
      makeDummyDefinition(definition.exceptionHandlerFactory),
      expressionDefinition,
      definition.typesInformation
    )
  }

  implicit class ObjectProcessDefinition(definition: ProcessDefinition[ObjectDefinition]) {
    def withService(id: String, returnType: Class[_], params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> ObjectDefinition(params.toList, ClazzRef(returnType), List.empty)))

    def withService(id: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> ObjectDefinition.withParams(params.toList)))

    def withSourceFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories = definition.sourceFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withSinkFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sinkFactories = definition.sinkFactories + (typ -> (ObjectDefinition.withParams(params.toList), SinkAdditionalData(true))))

    def withExceptionHandlerFactory(params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(exceptionHandlerFactory = ObjectDefinition.withParams(params.toList))

    def withCustomStreamTransformer(id: String, returnType: Class[_], additionalData: CustomTransformerAdditionalData, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (id -> (ObjectDefinition(params.toList, ClazzRef(returnType), List()), additionalData)))

    def withSignalsWithTransformers(id: String, returnType: Class[_], transformers: Set[String], params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(signalsWithTransformers = definition.signalsWithTransformers + (id -> (ObjectDefinition(params.toList, ClazzRef(returnType), List()), transformers)))

  }

}
