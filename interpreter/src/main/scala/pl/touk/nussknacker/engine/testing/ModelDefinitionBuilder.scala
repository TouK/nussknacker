package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.{ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.concurrent.Future

object ModelDefinitionBuilder {

  val emptyExpressionConfig: ExpressionConfigDefinition[ComponentStaticDefinition] = ExpressionConfigDefinition(
    Map.empty[String, ComponentStaticDefinition],
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
  )

  val empty: ModelDefinition[ComponentStaticDefinition] = {

    ModelDefinition(
      List.empty[(String, ComponentStaticDefinition)],
      emptyExpressionConfig,
      ClassExtractionSettings.Default
    )
  }

  def withNullImplementation(
      definition: ModelDefinition[ComponentStaticDefinition]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {
    definition.transform(wrapWithNullImplementation)
  }

  def toDefinitionWithImpl(
      expressionConfig: ExpressionConfigDefinition[ComponentStaticDefinition]
  ): ExpressionConfigDefinition[ComponentDefinitionWithImplementation] =
    ExpressionConfigDefinition(
      expressionConfig.globalVariables.mapValuesNow(wrapWithNullImplementation),
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
      staticDefinition: ComponentStaticDefinition
  ): ComponentDefinitionWithImplementation =
    MethodBasedComponentDefinitionWithImplementation(
      ComponentImplementationInvoker.nullImplementationInvoker,
      null,
      staticDefinition
    )

  implicit class ComponentDefinitionBuilder(definition: ModelDefinition[ComponentStaticDefinition]) {

    def withGlobalVariable(name: String, typ: TypingResult): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(expressionConfig =
        definition.expressionConfig.copy(globalVariables =
          definition.expressionConfig.globalVariables + (name -> wrapWithStaticDefinition(
            List.empty,
            Some(typ),
            GlobalVariablesSpecificData
          ))
        )
      )

    def withService(
        name: String,
        returnType: Option[TypingResult],
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.withComponent(name, wrapWithStaticServiceDefinition(params.toList, returnType))

    def withService(name: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.withComponent(name, wrapWithStaticServiceDefinition(params.toList, Some(Unknown)))

    def withSourceFactory(name: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.withComponent(name, wrapWithStaticSourceDefinition(params.toList, Some(Unknown)))

    def withSourceFactory(
        name: String,
        category: String,
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.withComponent(
        name,
        ComponentStaticDefinition(
          params.toList,
          Some(Unknown),
          Some(List(category)),
          SingleComponentConfig.zero,
          componentTypeSpecificData = SourceSpecificData
        )
      )

    def withSinkFactory(name: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.withComponent(name, wrapWithStaticSinkDefinition(params.toList, None))

    def withCustomStreamTransformer(
        name: String,
        returnType: Option[TypingResult],
        componentSpecificData: CustomComponentSpecificData,
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.withComponent(
        name,
        wrapWithStaticCustomComponentDefinition(
          params.toList,
          returnType,
          componentSpecificData
        )
      )

  }

  def wrapWithStaticSourceDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(parameters, returnType, SourceSpecificData)

  def wrapWithStaticSinkDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(parameters, returnType, SinkSpecificData)

  def wrapWithStaticServiceDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(parameters, returnType, ServiceSpecificData)

  def wrapWithStaticCustomComponentDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(parameters, returnType, componentSpecificData)

  def wrapWithStaticGlobalVariableDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(parameters, returnType, GlobalVariablesSpecificData)

  private def wrapWithStaticDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult],
      componentTypeSpecificData: ComponentTypeSpecificData
  ): ComponentStaticDefinition = {
    ComponentStaticDefinition(
      parameters,
      returnType,
      None,
      SingleComponentConfig.zero,
      componentTypeSpecificData
    )
  }

}
