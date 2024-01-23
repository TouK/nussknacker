package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, ComponentInfo}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.{
  ExpressionConfigDefinition,
  GlobalVariableDefinitionWithImplementation
}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.emptyExpressionConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

final case class ModelDefinitionBuilder(
    componentInfoToId: ComponentInfo => ComponentId,
    components: List[(String, ComponentDefinitionWithImplementation)],
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
    wrapService(name, ComponentStaticDefinition(params.toList, returnType))

  def withSource(name: String, params: Parameter*): ModelDefinitionBuilder =
    withSource(name, Some(Unknown), params: _*)

  def withSource(name: String, returnType: Option[TypingResult], params: Parameter*): ModelDefinitionBuilder =
    withSource(name, ComponentStaticDefinition(params.toList, returnType))

  def withSink(name: String, params: Parameter*): ModelDefinitionBuilder =
    withSink(name, ComponentStaticDefinition(params.toList, None))

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      params: Parameter*
  ): ModelDefinitionBuilder =
    withCustom(name, returnType, componentSpecificData, componentGroupName = None, componentId = None, params: _*)

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      componentGroupName: Option[ComponentGroupName],
      componentId: Option[ComponentId],
      params: Parameter*
  ): ModelDefinitionBuilder =
    wrapCustom(
      name,
      ComponentStaticDefinition(params.toList, returnType),
      componentSpecificData,
      componentGroupName,
      componentId
    )

  def withGlobalVariable(name: String, variable: AnyRef): ModelDefinitionBuilder = {
    copy(globalVariables = globalVariables + (name -> variable))
  }

  private def withSource(
      name: String,
      staticDefinition: ComponentStaticDefinition
  ): ModelDefinitionBuilder =
    withComponent(name, staticDefinition, SourceSpecificData, componentGroupName = None, componentId = None)

  private def withSink(
      name: String,
      staticDefinition: ComponentStaticDefinition,
  ): ModelDefinitionBuilder =
    withComponent(name, staticDefinition, SinkSpecificData, componentGroupName = None, componentId = None)

  private def wrapService(
      name: String,
      staticDefinition: ComponentStaticDefinition,
  ): ModelDefinitionBuilder =
    withComponent(name, staticDefinition, ServiceSpecificData, componentGroupName = None, componentId = None)

  private def wrapCustom(
      name: String,
      staticDefinition: ComponentStaticDefinition,
      componentSpecificData: CustomComponentSpecificData,
      componentGroupName: Option[ComponentGroupName],
      componentId: Option[ComponentId]
  ): ModelDefinitionBuilder =
    withComponent(name, staticDefinition, componentSpecificData, componentGroupName, componentId)

  private def withComponent(
      name: String,
      staticDefinition: ComponentStaticDefinition,
      componentTypeSpecificData: ComponentTypeSpecificData,
      componentGroupName: Option[ComponentGroupName],
      componentId: Option[ComponentId]
  ): ModelDefinitionBuilder = {
    val defaultConfig =
      DefaultComponentConfigDeterminer.forNotBuiltInComponentType(
        componentTypeSpecificData,
        staticDefinition.returnType.isDefined
      )
    val configWithOverridenGroupName =
      componentGroupName.map(group => defaultConfig.copy(componentGroup = Some(group))).getOrElse(defaultConfig)
    val info = ComponentInfo(componentTypeSpecificData.componentType, name)
    val configWithComponentId =
      configWithOverridenGroupName.copy(componentId = Some(componentId.getOrElse(componentInfoToId(info))))
    ComponentDefinitionExtractor
      .filterOutDisabledAndComputeFinalUiDefinition(
        configWithComponentId,
        groupName => groupNameMapping.getOrElse(groupName, Some(groupName))
      )
      .map { case (uiDefinition, _) =>
        MethodBasedComponentDefinitionWithImplementation.withNullImplementation(
          componentTypeSpecificData,
          staticDefinition,
          uiDefinition
        )
      }
      .map { component =>
        copy(components = (name -> component) :: components)
      }
      .getOrElse(this)
  }

  def build: ModelDefinition = {
    val globalVariablesDefinition: Map[String, GlobalVariableDefinitionWithImplementation] =
      globalVariables.mapValuesNow(GlobalVariableDefinitionWithImplementation(_))
    ModelDefinition(
      components,
      emptyExpressionConfig.copy(globalVariables = globalVariablesDefinition),
      ClassExtractionSettings.Default
    )
  }

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

  val emptyExpressionConfig: ExpressionConfigDefinition =
    ExpressionConfigDefinition(
      Map.empty[String, GlobalVariableDefinitionWithImplementation],
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

}
