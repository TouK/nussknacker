package pl.touk.nussknacker.engine.testing

import pl.touk.nussknacker.engine.api.SpelExpressionExcludeList
import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentId,
  DesignerWideComponentId,
  ProcessingMode
}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.process.ExpressionConfig._
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
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
    determineDesignerWideId: ComponentId => DesignerWideComponentId,
    components: List[ComponentDefinitionWithImplementation],
    globalVariables: Map[String, AnyRef],
    private val groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]
) {

  def withDesignerWideComponentIdDeterminingStrategy(
      determineDesignerWideId: ComponentId => DesignerWideComponentId
  ): ModelDefinitionBuilder =
    copy(determineDesignerWideId = determineDesignerWideId)

  def withService(name: String, params: Parameter*): ModelDefinitionBuilder =
    withService(name, Some(Unknown), params: _*)

  def withService(
      name: String,
      returnType: Option[TypingResult],
      params: Parameter*
  ): ModelDefinitionBuilder =
    wrapService(name, ComponentStaticDefinition(params.toList, returnType))

  def withUnboundedStreamSource(name: String, params: Parameter*): ModelDefinitionBuilder =
    withUnboundedStreamSource(name, Some(Unknown), params: _*)

  def withUnboundedStreamSource(
      name: String,
      returnType: Option[TypingResult],
      params: Parameter*
  ): ModelDefinitionBuilder =
    withSource(
      name,
      ComponentStaticDefinition(params.toList, returnType),
      AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream)
    )

  def withSink(name: String, params: Parameter*): ModelDefinitionBuilder =
    withSink(name, ComponentStaticDefinition(params.toList, None))

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      params: Parameter*
  ): ModelDefinitionBuilder =
    withCustom(
      name,
      returnType,
      componentSpecificData,
      componentGroupName = None,
      designerWideComponentId = None,
      params: _*
    )

  def withCustom(
      name: String,
      returnType: Option[TypingResult],
      componentSpecificData: CustomComponentSpecificData,
      componentGroupName: Option[ComponentGroupName],
      designerWideComponentId: Option[DesignerWideComponentId],
      params: Parameter*
  ): ModelDefinitionBuilder =
    wrapCustom(
      name,
      ComponentStaticDefinition(params.toList, returnType),
      componentSpecificData,
      componentGroupName,
      designerWideComponentId
    )

  def withGlobalVariable(name: String, variable: AnyRef): ModelDefinitionBuilder = {
    copy(globalVariables = globalVariables + (name -> variable))
  }

  private def withSource(
      name: String,
      staticDefinition: ComponentStaticDefinition,
      allowedProcessingModes: AllowedProcessingModes
  ): ModelDefinitionBuilder =
    withComponent(
      name,
      staticDefinition,
      SourceSpecificData,
      componentGroupName = None,
      designerWideComponentId = None,
      allowedProcessingModes
    )

  private def withSink(
      name: String,
      staticDefinition: ComponentStaticDefinition,
  ): ModelDefinitionBuilder =
    withComponent(
      name,
      staticDefinition,
      SinkSpecificData,
      componentGroupName = None,
      designerWideComponentId = None,
      allowedProcessingModes = AllowedProcessingModes.All
    )

  private def wrapService(
      name: String,
      staticDefinition: ComponentStaticDefinition,
  ): ModelDefinitionBuilder =
    withComponent(
      name,
      staticDefinition,
      ServiceSpecificData,
      componentGroupName = None,
      designerWideComponentId = None,
      allowedProcessingModes = AllowedProcessingModes.All
    )

  private def wrapCustom(
      name: String,
      staticDefinition: ComponentStaticDefinition,
      componentSpecificData: CustomComponentSpecificData,
      componentGroupName: Option[ComponentGroupName],
      designerWideComponentId: Option[DesignerWideComponentId]
  ): ModelDefinitionBuilder =
    withComponent(
      name,
      staticDefinition,
      componentSpecificData,
      componentGroupName,
      designerWideComponentId,
      allowedProcessingModes = AllowedProcessingModes.All
    )

  private def withComponent(
      name: String,
      staticDefinition: ComponentStaticDefinition,
      componentTypeSpecificData: ComponentTypeSpecificData,
      componentGroupName: Option[ComponentGroupName],
      designerWideComponentId: Option[DesignerWideComponentId],
      allowedProcessingModes: AllowedProcessingModes
  ): ModelDefinitionBuilder = {
    val defaultConfig =
      DefaultComponentConfigDeterminer.forNotBuiltInComponentType(
        componentTypeSpecificData.componentType,
        staticDefinition.returnType.isDefined,
        Option(componentTypeSpecificData).collect { case CustomComponentSpecificData(_, canBeEnding) =>
          canBeEnding
        }
      )
    val id = ComponentId(componentTypeSpecificData.componentType, name)
    val configWithDesignerWideComponentId =
      defaultConfig.copy(
        componentGroup = componentGroupName.orElse(defaultConfig.componentGroup),
        componentId = Some(designerWideComponentId.getOrElse(determineDesignerWideId(id)))
      )
    ComponentDefinitionExtractor
      .filterOutDisabledAndComputeFinalUiDefinition(
        configWithDesignerWideComponentId,
        groupName => groupNameMapping.getOrElse(groupName, Some(groupName))
      )
      .map { case (uiDefinition, _) =>
        MethodBasedComponentDefinitionWithImplementation.withNullImplementation(
          name,
          componentTypeSpecificData,
          staticDefinition,
          uiDefinition,
          allowedProcessingModes
        )
      }
      .map { component =>
        copy(components = component :: components)
      }
      .getOrElse(this)
  }

  def build: ModelDefinition = {
    val globalVariablesDefinition: Map[String, GlobalVariableDefinitionWithImplementation] =
      globalVariables.mapValuesNow(GlobalVariableDefinitionWithImplementation(_))

    val componentDefinitionExtractionMode = ComponentDefinitionExtractionMode.FinalDefinition
    ModelDefinition(
      Components.empty(componentDefinitionExtractionMode).withComponents(components),
      emptyExpressionConfig.copy(globalVariables = globalVariablesDefinition),
      ClassExtractionSettings.Default
    )
  }

}

object ModelDefinitionBuilder {

  val empty: ModelDefinitionBuilder = empty(groupNameMapping = Map.empty)

  def empty(groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]): ModelDefinitionBuilder = {
    new ModelDefinitionBuilder(
      determineDesignerWideId = id => DesignerWideComponentId(id.toString),
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
      optimizeCompilation = true,
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
