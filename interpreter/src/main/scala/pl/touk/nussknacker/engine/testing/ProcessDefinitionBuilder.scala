package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ExpressionConfig.{
  defaultAdditionalClasses,
  defaultDynamicPropertyAccessAllowed,
  defaultMethodExecutionForUnknownAllowed,
  defaultStaticMethodInvocationsChecking,
  defaultStrictMethodsChecking,
  defaultStrictTypeChecking
}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{
  ComponentImplementationInvoker,
  ObjectDefinition,
  ObjectWithMethodDef,
  StandardObjectWithMethodDef
}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{
  CustomTransformerAdditionalData,
  ExpressionDefinition,
  ProcessDefinition
}
import pl.touk.nussknacker.engine.util.Implicits._

import scala.concurrent.Future

object ProcessDefinitionBuilder {

  def empty: ProcessDefinition[ObjectDefinition] =
    ProcessDefinition(
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      ExpressionDefinition(
        Map.empty,
        List.empty,
        defaultAdditionalClasses,
        languages = LanguageConfiguration.default,
        optimizeCompilation = true,
        strictTypeChecking = defaultStrictTypeChecking,
        dictionaries = Map.empty,
        hideMetaVariable = false,
        strictMethodsChecking = defaultStrictMethodsChecking,
        staticMethodInvocationsChecking = defaultStaticMethodInvocationsChecking,
        methodExecutionForUnknownAllowed = defaultMethodExecutionForUnknownAllowed,
        dynamicPropertyAccessAllowed = defaultDynamicPropertyAccessAllowed,
        spelExpressionExcludeList = SpelExpressionExcludeList.default,
        customConversionsProviders = List.empty
      ),
      ClassExtractionSettings.Default
    )

  def withEmptyObjects(definition: ProcessDefinition[ObjectDefinition]): ProcessDefinition[ObjectWithMethodDef] = {
    val expressionConfig     = definition.expressionConfig
    val expressionDefinition = toExpressionDefinition(expressionConfig)
    ProcessDefinition(
      definition.services.mapValuesNow(makeDummyDefinition(_, classOf[Future[_]])),
      definition.sourceFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.sinkFactories.mapValuesNow(makeDummyDefinition(_)),
      definition.customStreamTransformers.mapValuesNow { case (transformer, queryNames) =>
        (makeDummyDefinition(transformer), queryNames)
      },
      expressionDefinition,
      definition.settings
    )
  }

  def toExpressionDefinition(
      expressionConfig: ExpressionDefinition[ObjectDefinition]
  ): ExpressionDefinition[ObjectWithMethodDef] =
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
      expressionConfig.customConversionsProviders
    )

  private def makeDummyDefinition(
      objectDefinition: ObjectDefinition,
      realType: Class[_] = classOf[Any]
  ): ObjectWithMethodDef =
    StandardObjectWithMethodDef(
      ComponentImplementationInvoker.nullImplementationInvoker,
      null,
      objectDefinition,
      None,
      realType
    )

  implicit class ObjectProcessDefinition(definition: ProcessDefinition[ObjectDefinition]) {

    def withGlobalVariable(name: String, typ: TypingResult): ProcessDefinition[ObjectDefinition] =
      definition.copy(expressionConfig =
        definition.expressionConfig.copy(globalVariables =
          definition.expressionConfig.globalVariables + (name -> objectDefinition(List.empty, Some(typ)))
        )
      )

    def withService(
        id: String,
        returnType: Option[TypingResult],
        params: Parameter*
    ): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> objectDefinition(params.toList, returnType)))

    def withService(id: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(services = definition.services + (id -> objectDefinition(params.toList, Some(Unknown))))

    def withSourceFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sourceFactories =
        definition.sourceFactories + (typ -> objectDefinition(params.toList, Some(Unknown)))
      )

    def withSinkFactory(typ: String, params: Parameter*): ProcessDefinition[ObjectDefinition] =
      definition.copy(sinkFactories = definition.sinkFactories + (typ -> objectDefinition(params.toList, None)))

    def withCustomStreamTransformer(
        id: String,
        returnType: Option[TypingResult],
        additionalData: CustomTransformerAdditionalData,
        params: Parameter*
    ): ProcessDefinition[ObjectDefinition] =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (id -> (objectDefinition(params.toList, returnType), additionalData))
      )

  }

  def objectDefinition(parameters: List[Parameter], returnType: Option[TypingResult]): ObjectDefinition = {
    ObjectDefinition(parameters, returnType, SingleComponentConfig.zero)
  }

}
