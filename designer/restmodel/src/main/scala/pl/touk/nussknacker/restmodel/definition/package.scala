package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData

import java.net.URI

package object definition {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  // This class contains various views on definitions, used in a different FE contexts
  @JsonCodec(encodeOnly = true) final case class UIDefinitions(
      componentGroups: List[UIComponentGroup],
      components: Map[ComponentId, UIComponentDefinition],
      classes: List[TypingResult],
      scenarioProperties: UiScenarioProperties,
      edgesForNodes: List[UINodeEdges],
  )

  @JsonCodec(encodeOnly = true) final case class UIValueParameter(
      name: String,
      typ: TypingResult,
      expression: Expression
  )

  @JsonCodec final case class UIParameter(
      name: String,
      typ: TypingResult,
      editor: ParameterEditor,
      // It it used for node parameter adjustment on FE side (see ParametersUtils.ts -> adjustParameters)
      defaultValue: Expression,
      // additionalVariables and variablesToHide are served to FE because suggestions API requires full set of variables
      // and ScenarioWithDetails.json.validationResult.nodeResults is not enough
      additionalVariables: Map[String, TypingResult],
      variablesToHide: Set[String],
      // FE need this information because branch parameters aren't changed dynamically during node validation so they never
      // should be invalidated
      branchParam: Boolean,
      hintText: Option[String],
      label: String,
      // This attribute is in use only by the external project and was introduced in the 1.18 version
      // The option is for decoder backward compatibility to decode responses from older versions
      // The option can be removed in future releases
      requiredParam: Option[Boolean],
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
      icon: String,
      docsUrl: Option[String],
      // This field is defined only for fragments
      outputParameters: Option[List[String]]
  )

  @JsonCodec final case class UISourceParameters(sourceId: String, parameters: List[UIParameter])

  final case class UINodeEdges(
      componentId: ComponentId,
      edges: List[EdgeType],
      canChooseNodes: Boolean,
      isForInputDefinition: Boolean
  )

  object UINodeEdges {
    implicit val componentIdEncoder: Encoder[ComponentId] = Encoder.encodeString.contramap(_.toString)

    implicit val encoder: Encoder[UINodeEdges] = deriveConfiguredEncoder
  }

  object UIComponentNodeTemplate {

    def create(
        componentId: ComponentId,
        nodeTemplate: NodeData,
        branchParametersTemplate: List[NodeParameter]
    ): UIComponentNodeTemplate =
      UIComponentNodeTemplate(
        componentId,
        componentId.name,
        nodeTemplate,
        branchParametersTemplate
      )

  }

  @JsonCodec(encodeOnly = true) final case class UIComponentNodeTemplate(
      // componentId is used as a key in a DOM model - see ToolboxComponentGroup
      componentId: ComponentId,
      label: String,
      node: NodeData,
      branchParametersTemplate: List[NodeParameter] = List.empty
  )

  @JsonCodec(encodeOnly = true) final case class UIComponentGroup(
      name: ComponentGroupName,
      components: List[UIComponentNodeTemplate]
  )

  @JsonCodec final case class UiScenarioProperties(
      propertiesConfig: Map[String, UiScenarioPropertyConfig],
      docsUrl: Option[String]
  )

  @JsonCodec final case class UiScenarioPropertyConfig(
      // This attribute is deprecated on BE and FE as it's not used anywhere.
      // Right now it's only kept because an external project uses it but it will be removed in the future.
      defaultValue: Option[String],
      editor: ParameterEditor,
      label: Option[String],
      hintText: Option[String]
  )

  @JsonCodec final case class UiActivityParameterConfig(
      defaultValue: Option[String],
      editor: ParameterEditor,
      label: Option[String],
      hintText: Option[String]
  )

  object UIParameter {
    implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[UIParameter] =
      deriveConfiguredDecoder[UIParameter]
  }

}
