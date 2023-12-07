package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component.implinvoker.ComponentImplementationInvoker
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentStaticDefinition,
  MethodBasedComponentDefinitionWithImplementation
}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.model.{CustomTransformerAdditionalData, ModelDefinition}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.concurrent.Future

object ModelDefinitionBuilder {

  def empty: ModelDefinition[ComponentStaticDefinition] =
    ModelDefinition(
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

  def withNullImplementation(
      definition: ModelDefinition[ComponentStaticDefinition]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {
    val expressionConfig     = definition.expressionConfig
    val expressionDefinition = toExpressionDefinition(expressionConfig)
    ModelDefinition(
      definition.services.mapValuesNow(wrapWithNullImplementation(_, classOf[Future[_]])),
      definition.sourceFactories.mapValuesNow(wrapWithNullImplementation(_)),
      definition.sinkFactories.mapValuesNow(wrapWithNullImplementation(_)),
      definition.customStreamTransformers.mapValuesNow { case (transformer, queryNames) =>
        (wrapWithNullImplementation(transformer), queryNames)
      },
      expressionDefinition,
      definition.settings
    )
  }

  def toExpressionDefinition(
      expressionConfig: ExpressionDefinition[ComponentStaticDefinition]
  ): ExpressionDefinition[ComponentDefinitionWithImplementation] =
    ExpressionDefinition(
      expressionConfig.globalVariables.mapValuesNow(wrapWithNullImplementation(_)),
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

  private def wrapWithNullImplementation(
      objectDefinition: ComponentStaticDefinition,
      realType: Class[_] = classOf[Any]
  ): ComponentDefinitionWithImplementation =
    MethodBasedComponentDefinitionWithImplementation(
      ComponentImplementationInvoker.nullImplementationInvoker,
      null,
      objectDefinition,
      realType
    )

  implicit class ComponentDefinitionBuilder(definition: ModelDefinition[ComponentStaticDefinition]) {

    def withGlobalVariable(name: String, typ: TypingResult): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(expressionConfig =
        definition.expressionConfig.copy(globalVariables =
          definition.expressionConfig.globalVariables + (name -> wrapWithStaticDefinition(List.empty, Some(typ)))
        )
      )

    def withService(
        id: String,
        returnType: Option[TypingResult],
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(services = definition.services + (id -> wrapWithStaticDefinition(params.toList, returnType)))

    def withService(id: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(services = definition.services + (id -> wrapWithStaticDefinition(params.toList, Some(Unknown))))

    def withSourceFactory(typ: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(sourceFactories =
        definition.sourceFactories + (typ -> wrapWithStaticDefinition(params.toList, Some(Unknown)))
      )

    def withSourceFactory(
        typ: String,
        category: String,
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(sourceFactories =
        definition.sourceFactories + (typ -> ComponentStaticDefinition(
          params.toList,
          Some(Unknown),
          Some(List(category)),
          SingleComponentConfig.zero
        ))
      )

    def withSinkFactory(typ: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(sinkFactories = definition.sinkFactories + (typ -> wrapWithStaticDefinition(params.toList, None)))

    def withCustomStreamTransformer(
        id: String,
        returnType: Option[TypingResult],
        additionalData: CustomTransformerAdditionalData,
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(customStreamTransformers =
        definition.customStreamTransformers + (id -> (wrapWithStaticDefinition(
          params.toList,
          returnType
        ), additionalData))
      )

  }

  def wrapWithStaticDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition = {
    ComponentStaticDefinition(parameters, returnType, None, SingleComponentConfig.zero)
  }

}
