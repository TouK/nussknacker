package pl.touk.nussknacker.restmodel

import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentInfo, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.deployment.CustomAction
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.{EdgeType, evaluatedparam}

import java.net.URI

package object definition {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  @JsonCodec(encodeOnly = true) final case class UIProcessObjects(
      componentGroups: List[ComponentGroup],
      // TODO: rename to modelDefinition
      processDefinition: UIModelDefinition,
      componentsConfig: Map[String, SingleComponentConfig],
      scenarioPropertiesConfig: Map[String, UiScenarioPropertyConfig],
      edgesForNodes: List[NodeEdges],
      customActions: List[UICustomAction],
      defaultAsyncInterpretation: Boolean
  )

  // TODO We should map components by ComponentId, not by `label` like currently, and keep `label` in SingleComponentConfig
  // TODO We should keep all components in a single map, not distinguishing between ContentTypes
  @JsonCodec(encodeOnly = true) final case class UIModelDefinition(
      services: Map[String, UIComponentDefinition],
      sourceFactories: Map[String, UIComponentDefinition],
      sinkFactories: Map[String, UIComponentDefinition],
      customStreamTransformers: Map[String, UIComponentDefinition],
      typesInformation: Set[UIClassDefinition],
      fragmentInputs: Map[String, UIFragmentComponentDefinition]
  )

  @JsonCodec(encodeOnly = true) final case class UIClassDefinition(
      clazzName: TypingResult
  )

  @JsonCodec(encodeOnly = true) final case class UIValueParameter(
      name: String,
      typ: TypingResult,
      expression: Expression
  )

  @JsonCodec(encodeOnly = true) final case class UIParameter(
      name: String,
      typ: TypingResult,
      editor: ParameterEditor,
      // added for backward compatibility with Nu 1.12
      validators: List[ParameterValidator] = List.empty,
      defaultValue: Expression,
      // additionalVariables and variablesToHide are served to FE because suggestions API requires full set of variables
      // and ScenarioWithDetails.json.validationResult.nodeResults is not enough
      additionalVariables: Map[String, TypingResult],
      variablesToHide: Set[String],
      // FE need this information because branch parameters aren't changed dynamically during node validation so they never
      // should be invalidated
      branchParam: Boolean,
      hintText: Option[String],
      label: String = "",
  )

  @JsonCodec(encodeOnly = true) final case class UIComponentDefinition(
      // These parameters are mostly used for method based, static components. For dynamic components, it is the last fallback
      // when scenario validation doesn't returned node results (e.g. when DisplayableProcess can't be translated to CanonicalProcess).
      // And node validation wasn't performed yet (e.g. just after node details modal open) or for branch parameters
      // which aren't handled dynamically. See getDynamicParameterDefinitions in selectors.tsx.
      parameters: List[UIParameter],
      // TODO: remove this field
      // We use it for two purposes:
      // 1. Because we have a special "Output variable name" parameter which is treated specially both in scenario format
      //    (see CustomNode.outputVar and Join.outputVar) and accordingly in the component definition
      //    We can easily move this parameter to normal parameters but in the join case, it will change the order parameters
      //    (it will be after branch parameters instead of before them)
      // 2. We have a heuristic that trying to figure out context of variables to pass to node validation and to suggestions
      //    (see. ProcessUtils.findAvailableVariables). This heuristic is used when DisplayableProcess can't be translated
      //    to CanonicalProcess. When we replace CanonicalProcess by DisplayableProcess, it won't be needed anymore
      returnType: Option[TypingResult],
      categories: List[String],
  )

  @JsonCodec(encodeOnly = true) final case class UIFragmentComponentDefinition(
      parameters: List[UIParameter],
      outputParameters: List[String],
      returnType: Option[TypingResult],
      categories: List[String]
  )

  @JsonCodec(encodeOnly = true) final case class UISourceParameters(sourceId: String, parameters: List[UIParameter])

  @JsonCodec final case class NodeTypeId(`type`: String, id: Option[String] = None)

  @JsonCodec final case class NodeEdges(
      nodeId: NodeTypeId,
      edges: List[EdgeType],
      canChooseNodes: Boolean,
      isForInputDefinition: Boolean
  )

  object ComponentNodeTemplate {

    def create(
        componentInfo: ComponentInfo,
        node: NodeData,
        categories: List[String],
        branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty
    ): ComponentNodeTemplate =
      ComponentNodeTemplate(componentInfo.`type`, componentInfo.name, node, categories, branchParametersTemplate)

  }

  @JsonCodec(encodeOnly = true) final case class ComponentNodeTemplate(
      `type`: ComponentType,
      // TODO: Rename to name
      label: String,
      node: NodeData,
      // TODO: remove
      categories: List[String],
      branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty,
      // TODO: This field is added temporary to pick correct icon - we shouldn't use this class for other purposes than encoding to json
      isEnricher: Option[Boolean] = None
  ) {
    // TODO: This is temporary - we shouldn't use this class for other purposes than encoding to json
    def componentInfo: ComponentInfo = ComponentInfo(`type`, label)
  }

  @JsonCodec(encodeOnly = true) final case class ComponentGroup(
      name: ComponentGroupName,
      components: List[ComponentNodeTemplate]
  )

  @JsonCodec final case class UiScenarioPropertyConfig(
      defaultValue: Option[String],
      editor: ParameterEditor,
      label: Option[String]
  )

  object UIParameter {
    implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[UIParameter] =
      deriveConfiguredDecoder[UIParameter]
  }

  object UICustomAction {

    def apply(action: CustomAction): UICustomAction = UICustomAction(
      name = action.name,
      allowedStateStatusNames = action.allowedStateStatusNames,
      icon = action.icon,
      parameters = action.parameters.map(p => UICustomActionParameter(p.name, p.editor))
    )

  }

  @JsonCodec final case class UICustomAction(
      name: String,
      allowedStateStatusNames: List[String],
      icon: Option[URI],
      parameters: List[UICustomActionParameter]
  )

  @JsonCodec final case class UICustomActionParameter(name: String, editor: ParameterEditor)

}
