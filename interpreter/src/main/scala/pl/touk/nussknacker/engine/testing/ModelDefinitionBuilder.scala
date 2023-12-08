package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentInfo, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  ComponentTypeSpecificData,
  CustomComponentSpecificData,
  NoComponentTypeSpecificData
}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.concurrent.Future

object ModelDefinitionBuilder {

  val emptyExpressionDefinition: ExpressionDefinition[ComponentStaticDefinition] = ExpressionDefinition(
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
      emptyExpressionDefinition,
      ClassExtractionSettings.Default
    )
  }

  def withNullImplementation(
      definition: ModelDefinition[ComponentStaticDefinition]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {
    definition.transform { component =>
      val realType = if (component.componentType == ComponentType.Service) classOf[Future[_]] else classOf[Any]
      wrapWithNullImplementation(component, realType)
    }
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
      staticDefinition: ComponentStaticDefinition,
      runtimeClass: Class[_] = classOf[Any]
  ): ComponentDefinitionWithImplementation =
    MethodBasedComponentDefinitionWithImplementation(
      ComponentImplementationInvoker.nullImplementationInvoker,
      null,
      staticDefinition,
      runtimeClass
    )

  implicit class ComponentDefinitionBuilder(definition: ModelDefinition[ComponentStaticDefinition]) {

    def withGlobalVariable(name: String, typ: TypingResult): ModelDefinition[ComponentStaticDefinition] =
      definition.copy(expressionConfig =
        definition.expressionConfig.copy(globalVariables =
          definition.expressionConfig.globalVariables + (name -> wrapWithStaticDefinition(
            ComponentType.BuiltIn,
            List.empty,
            Some(typ),
            NoComponentTypeSpecificData
          ))
        )
      )

    def withService(
        name: String,
        returnType: Option[TypingResult],
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.addComponent(name, wrapWithStaticServiceDefinition(params.toList, returnType))

    def withService(name: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.addComponent(name, wrapWithStaticServiceDefinition(params.toList, Some(Unknown)))

    def withSourceFactory(name: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.addComponent(name, wrapWithStaticSourceDefinition(params.toList, Some(Unknown)))

    def withSourceFactory(
        name: String,
        category: String,
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.addComponent(
        name,
        ComponentStaticDefinition(
          ComponentType.Source,
          params.toList,
          Some(Unknown),
          Some(List(category)),
          SingleComponentConfig.zero,
          componentTypeSpecificData = NoComponentTypeSpecificData
        )
      )

    def withSinkFactory(name: String, params: Parameter*): ModelDefinition[ComponentStaticDefinition] =
      definition.addComponent(name, wrapWithStaticSinkDefinition(params.toList, None))

    def withCustomStreamTransformer(
        name: String,
        returnType: Option[TypingResult],
        componentSpecificData: CustomComponentSpecificData,
        params: Parameter*
    ): ModelDefinition[ComponentStaticDefinition] =
      definition.addComponent(
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
    wrapWithStaticDefinition(ComponentType.Source, parameters, returnType, NoComponentTypeSpecificData)

  def wrapWithStaticSinkDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(ComponentType.Sink, parameters, returnType, NoComponentTypeSpecificData)

  def wrapWithStaticServiceDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(ComponentType.Service, parameters, returnType, NoComponentTypeSpecificData)

  def wrapWithStaticCustomComponentDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(ComponentType.CustomComponent, parameters, returnType, componentSpecificData)

  def wrapWithStaticGlobalVariableDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): ComponentStaticDefinition =
    wrapWithStaticDefinition(ComponentType.BuiltIn, parameters, returnType, NoComponentTypeSpecificData)

  private def wrapWithStaticDefinition(
      componentType: ComponentType,
      parameters: List[Parameter],
      returnType: Option[TypingResult],
      componentTypeSpecificData: ComponentTypeSpecificData
  ): ComponentStaticDefinition = {
    ComponentStaticDefinition(
      componentType,
      parameters,
      returnType,
      None,
      SingleComponentConfig.zero,
      componentTypeSpecificData
    )
  }

}
