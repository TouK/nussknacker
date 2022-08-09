package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.util.Implicits._

import scala.concurrent.Future

object ProcessDefinitionBuilder {

  def empty: ProcessDefinition[ObjectDefinition] =
    ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty,
      ExpressionDefinition(Map.empty, List.empty, List.empty, languages = LanguageConfiguration(List.empty),
        optimizeCompilation = true, strictTypeChecking = true, dictionaries = Map.empty, hideMetaVariable = false,
        strictMethodsChecking = true, staticMethodInvocationsChecking = false, methodExecutionForUnknownAllowed = false,
        dynamicPropertyAccessAllowed = false, spelExpressionExcludeList = SpelExpressionExcludeList.default,
        customConversionsProviders = List.empty), ClassExtractionSettings.Default)

  def withEmptyObjects(definition: ProcessDefinition[ObjectDefinition]): ProcessDefinition[ObjectWithMethodDef] = {
    val expressionConfig = definition.expressionConfig
    val expressionDefinition = toExpressionDefinition(expressionConfig)
    ProcessDefinition(
      definition.services.mapValuesNow(makeDummyDefinition(_, classOf[Future[_]])),
      definition.sourceFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.sinkFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.customStreamTransformers.mapValuesNow { case (transformer, queryNames) => (makeDummyDefinition(transformer), queryNames) },
      definition.signalsWithTransformers.mapValuesNow(sign => (makeDummyDefinition(sign._1), sign._2)),
      expressionDefinition,
      definition.settings
    )
  }

  private def toExpressionDefinition(expressionConfig: ExpressionDefinition[ObjectDefinition]) =
    ExpressionDefinition(
      expressionConfig.globalVariables.mapValuesNow(makeDummyDefinition(_)),
      expressionConfig.globalImports,
      expressionConfig.additionalClasses,
      expressionConfig.languages,
      expressionConfig.optimizeCompilation,
      expressionConfig.strictTypeChecking,
      expressionConfig.dictionaries,
      expressionConfig.hideMetaVariable,
      expressionConfig.strictMethodsChecking,
      expressionConfig.staticMethodInvocationsChecking,
      expressionConfig.methodExecutionForUnknownAllowed,
      expressionConfig.dynamicPropertyAccessAllowed,
      expressionConfig.spelExpressionExcludeList,
      expressionConfig.customConversionsProviders)

  private def makeDummyDefinition(objectDefinition: ObjectDefinition, realType: Class[_] = classOf[Any]): ObjectWithMethodDef =
    StandardObjectWithMethodDef(null, MethodDefinition("", (_, _) => null, new OrderedDependencies(objectDefinition.parameters),
      Unknown, realType, List()), objectDefinition)

  implicit class ObjectProcessDefinition(definition: ProcessDefinition[ObjectDefinition]) {
    def withService(id: String, returnType: Class[_], params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> ObjectDefinition(params.toList, Typed(returnType))))

    def withService(id: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> ObjectDefinition.withParams(params.toList)))

    def withSourceFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories = definition.sourceFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withSourceFactory(typ: String, category: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories = definition.sourceFactories + (typ -> new ObjectDefinition(params.toList, Unknown, Some(List(category)), SingleComponentConfig.zero)))

    def withSinkFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sinkFactories = definition.sinkFactories + (typ -> ObjectDefinition.withParams(params.toList)))

    def withCustomStreamTransformer(id: String, returnType: Class[_], additionalData: CustomTransformerAdditionalData, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (id -> (ObjectDefinition(params.toList, Typed(returnType)), additionalData)))

    def withSignalsWithTransformers(id: String, returnType: Class[_], transformers: Set[String], params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(signalsWithTransformers = definition.signalsWithTransformers + (id -> (ObjectDefinition(params.toList, Typed(returnType)), transformers)))

  }

}
