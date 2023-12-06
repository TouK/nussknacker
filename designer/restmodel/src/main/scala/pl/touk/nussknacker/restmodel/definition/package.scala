package pl.touk.nussknacker.restmodel

import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.engine.api.CirceUtil._
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

  @JsonCodec(encodeOnly = true) final case class UIProcessObjects(
      componentGroups: List[ComponentGroup],
      processDefinition: UIProcessDefinition,
      componentsConfig: Map[String, SingleComponentConfig],
      scenarioPropertiesConfig: Map[String, UiScenarioPropertyConfig],
      edgesForNodes: List[NodeEdges],
      customActions: List[UICustomAction],
      defaultAsyncInterpretation: Boolean
  )

  // TODO: in the future, we would like to map components by ComponentId, not by `label` like currently, and keep `label` in SingleComponentConfig
  // this would also make config merging logic in `UIProcessObjectsFactory.prepareUIProcessObjects` simpler
  @JsonCodec(encodeOnly = true) final case class UIProcessDefinition(
      services: Map[String, UIObjectDefinition],
      sourceFactories: Map[String, UIObjectDefinition],
      sinkFactories: Map[String, UIObjectDefinition],
      customStreamTransformers: Map[String, UIObjectDefinition],
      typesInformation: Set[UIClazzDefinition],
      fragmentInputs: Map[String, UIFragmentObjectDefinition]
  )

  @JsonCodec(encodeOnly = true) final case class UIClazzDefinition(
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
      validators: List[ParameterValidator],
      defaultValue: Expression,
      additionalVariables: Map[String, TypingResult],
      variablesToHide: Set[String],
      branchParam: Boolean,
      hintText: Option[String]
  )

  @JsonCodec(encodeOnly = true) final case class UIObjectDefinition(
      parameters: List[UIParameter],
      returnType: Option[TypingResult],
      categories: List[String],
  ) {

    def hasNoReturn: Boolean = returnType.isEmpty

  }

  @JsonCodec(encodeOnly = true) final case class UIFragmentObjectDefinition(
      parameters: List[UIParameter],
      outputParameters: List[String],
      returnType: Option[TypingResult],
      categories: List[String]
  ) {
    def toUIObjectDefinition: UIObjectDefinition =
      UIObjectDefinition(parameters, returnType, categories)
  }

  @JsonCodec(encodeOnly = true) final case class UISourceParameters(sourceId: String, parameters: List[UIParameter])

  @JsonCodec final case class NodeTypeId(`type`: String, id: Option[String] = None)

  @JsonCodec final case class NodeEdges(
      nodeId: NodeTypeId,
      edges: List[EdgeType],
      canChooseNodes: Boolean,
      isForInputDefinition: Boolean
  )

  import pl.touk.nussknacker.engine.graph.node.NodeData._

  object ComponentTemplate {

    def create(
        componentInfo: ComponentInfo,
        node: NodeData,
        categories: List[String],
        branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty
    ): ComponentTemplate =
      ComponentTemplate(componentInfo.`type`, componentInfo.name, node, categories, branchParametersTemplate)

  }

  // TODO: Rename to ComponentNodeTemplate
  @JsonCodec(encodeOnly = true) final case class ComponentTemplate(
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
      components: List[ComponentTemplate]
  )

  @JsonCodec final case class UiScenarioPropertyConfig(
      defaultValue: Option[String],
      editor: ParameterEditor,
      validators: List[ParameterValidator],
      label: Option[String]
  )

  object UIParameter {
    implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[UIParameter] =
      deriveConfiguredDecoder[UIParameter]
  }

  object UICustomAction {

    import pl.touk.nussknacker.restmodel.codecs.URICodecs.{uriDecoder, uriEncoder}

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
