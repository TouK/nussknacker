package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, ComponentInfo}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.{
  ExpressionConfigDefinition,
  GlobalVariableDefinitionExtractor
}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.emptyExpressionConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

final case class ModelDefinitionBuilder(
    componentInfoToId: ComponentInfo => ComponentId,
    components: List[(String, ComponentStaticDefinition)],
    globalVariables: Map[String, AnyRef],
    private val groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]
) {

  def withComponentInfoToId(componentInfoToId: ComponentInfo => ComponentId): ModelDefinitionBuilder =
    copy(componentInfoToId = componentInfoToId)

  def withService(name: String, params: Parameter*): ModelDefinitionBuilder =
    withService(name, Some(Unknown), params: _*)

  def withService(
      name: String,
      returnType: Option[TypingResult],
      params: Parameter*
  ): ModelDefinitionBuilder =
    wrapWithNotDisabledServiceDefinition(params.toList, returnType)
      .map(withComponent(name, _))
      .getOrElse(this)

  def withSource(name: String, params: Parameter*): ModelDefinitionBuilder =
    withSource(name, Some(Unknown), params: _*)

  def withSource(name: String, returnType: Option[TypingResult], params: Parameter*): ModelDefinitionBuilder =
    wrapWithNotDisabledSourceDefinition(params.toList, returnType)
      .map(withComponent(name, _))
      .getOrElse(this)

  def withSink(name: String, params: Parameter*): ModelDefinitionBuilder =
    wrapWithNotDisabledSinkDefinition(params.toList)
      .map(withComponent(name, _))
      .getOrElse(this)

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      params: Parameter*
  ): ModelDefinitionBuilder =
    withCustom(name, returnType, componentSpecificData, componentId = None, params: _*)

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      componentId: Option[ComponentId] = None,
  ): ModelDefinitionBuilder =
    withCustom(name, returnType, componentSpecificData, componentId, Seq.empty: _*)

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      componentId: Option[ComponentId],
      params: Parameter*
  ): ModelDefinitionBuilder =
    wrapWithNotDisabledCustomComponentDefinition(
      params.toList,
      returnType,
      componentSpecificData
    ).map(withComponent(name, _, componentId)).getOrElse(this)

  private def withComponent(
      name: String,
      componentStaticDefinition: ComponentStaticDefinition,
      componentId: Option[ComponentId] = None
  ): ModelDefinitionBuilder = {
    val config = componentStaticDefinition.componentConfig.copy(
      componentId = componentId.orElse(
        componentStaticDefinition.componentConfig.componentId.orElse(
          Some(componentInfoToId(ComponentInfo(componentStaticDefinition.componentType, name)))
        )
      )
    )

    copy(components = (name -> componentStaticDefinition.copy(componentConfig = config)) :: components)
  }

  def withGlobalVariable(name: String, variable: AnyRef): ModelDefinitionBuilder = {
    copy(globalVariables = globalVariables + (name -> variable))
  }

  private def wrapWithNotDisabledSourceDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): Option[ComponentStaticDefinition] =
    wrapWithNotDisabledStaticDefinition(parameters, returnType, SourceSpecificData)

  private def wrapWithNotDisabledSinkDefinition(
      parameters: List[Parameter],
  ): Option[ComponentStaticDefinition] =
    wrapWithNotDisabledStaticDefinition(parameters, None, SinkSpecificData)

  private def wrapWithNotDisabledServiceDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult]
  ): Option[ComponentStaticDefinition] =
    wrapWithNotDisabledStaticDefinition(parameters, returnType, ServiceSpecificData)

  private def wrapWithNotDisabledCustomComponentDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData
  ): Option[ComponentStaticDefinition] =
    wrapWithNotDisabledStaticDefinition(parameters, returnType, componentSpecificData)

  private def wrapWithNotDisabledStaticDefinition(
      parameters: List[Parameter],
      returnType: Option[TypingResult],
      componentTypeSpecificData: ComponentTypeSpecificData
  ): Option[ComponentStaticDefinition] = {
    val config =
      DefaultComponentConfigDeterminer.forNotBuiltInComponentType(componentTypeSpecificData, returnType.isDefined)
    ComponentDefinitionExtractor
      .translateGroupNameAndFilterOutDisabled(config, new ComponentsUiConfig(Map.empty, groupNameMapping))
      .map { case ConfigWithOriginalGroupName(configWithMappedName, originalGroupName) =>
        ComponentStaticDefinition(
          parameters,
          returnType,
          configWithMappedName,
          originalGroupName,
          componentTypeSpecificData
        )
      }
  }

  def build: ModelDefinition[ComponentDefinitionWithImplementation] = {
    val globalVariablesDefinition: Map[String, ComponentDefinitionWithImplementation] =
      globalVariables.mapValuesNow(GlobalVariableDefinitionExtractor.extractDefinition)
    ModelDefinition[ComponentDefinitionWithImplementation](
      components.map { case (k, v) => k -> withNullImplementation(v) },
      emptyExpressionConfig.copy(globalVariables = globalVariablesDefinition),
      ClassExtractionSettings.Default
    )
  }

  private def withNullImplementation(
      staticDefinition: ComponentStaticDefinition
  ): ComponentDefinitionWithImplementation =
    MethodBasedComponentDefinitionWithImplementation(
      ComponentImplementationInvoker.nullImplementationInvoker,
      null,
      staticDefinition
    )

}

object ModelDefinitionBuilder {

  val empty: ModelDefinitionBuilder = empty(groupNameMapping = Map.empty)

  def empty(groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]): ModelDefinitionBuilder = {
    new ModelDefinitionBuilder(
      componentInfoToId = info => ComponentId(info.toString),
      components = List.empty,
      globalVariables = Map.empty,
      groupNameMapping
    )
  }

  val emptyExpressionConfig: ExpressionConfigDefinition[ComponentDefinitionWithImplementation] =
    ExpressionConfigDefinition(
      Map.empty[String, ComponentDefinitionWithImplementation],
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

  implicit class ToStaticDefinitionConverter(modelDefinition: ModelDefinition[ComponentDefinitionWithImplementation]) {

    def toStaticComponentsDefinition: ModelDefinition[ComponentStaticDefinition] = modelDefinition.transform {
      case methodBased: MethodBasedComponentDefinitionWithImplementation => methodBased.staticDefinition
      case dynamic: DynamicComponentDefinitionWithImplementation =>
        throw new IllegalStateException(
          s"ModelDefinitionBuilder.toStaticComponentsDefinition used with a dynamic component: $dynamic"
        )
    }

  }

}
