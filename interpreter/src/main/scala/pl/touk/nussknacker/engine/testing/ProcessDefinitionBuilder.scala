package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ComponentImplementationInvoker, ObjectDefinition, ObjectWithMethodDef, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ComponentIdWithName, CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.util.Implicits._

import scala.concurrent.Future

object ProcessDefinitionBuilder {

  def empty: ProcessDefinition[ObjectDefinition] =
    ProcessDefinition(Map.empty, Map.empty, Map.empty, Map.empty,
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
    StandardObjectWithMethodDef(ComponentImplementationInvoker.nullImplementationInvoker,  null, objectDefinition, realType)

  implicit class ObjectProcessDefinition(definition: ProcessDefinition[ObjectDefinition]) {
    def withService(id: String, returnType: Option[TypingResult], params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (ComponentIdWithName(???, id) -> objectDefinition(params.toList, returnType)))

    def withService(id: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (ComponentIdWithName(???, id) -> objectDefinition(params.toList, Some(Unknown))))

    def withSourceFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories = definition.sourceFactories + (ComponentIdWithName(???, typ) -> objectDefinition(params.toList, Some(Unknown))))

    def withSourceFactory(typ: String, category: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories = definition.sourceFactories + (ComponentIdWithName(???, typ) -> ObjectDefinition(params.toList, Some(Unknown), Some(List(category)), SingleComponentConfig.zero)))

    def withSinkFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sinkFactories = definition.sinkFactories + (ComponentIdWithName(???, typ) -> objectDefinition(params.toList, None)))

    def withCustomStreamTransformer(id: String, returnType: Option[TypingResult], additionalData: CustomTransformerAdditionalData, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (ComponentIdWithName(???, id) -> (objectDefinition(params.toList, returnType), additionalData)))

  }
  
  def objectDefinition(parameters: List[Parameter], returnType: Option[TypingResult]): ObjectDefinition = {
    ObjectDefinition(parameters, returnType, None, SingleComponentConfig.zero)
  }
  
}
