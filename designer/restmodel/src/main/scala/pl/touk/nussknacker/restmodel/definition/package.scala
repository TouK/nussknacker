package pl.touk.nussknacker.restmodel

import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.deployment.CustomAction
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.{EdgeType, evaluatedparam}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

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
      globalVariables: Map[String, UIObjectDefinition],
      typesInformation: Set[UIClazzDefinition],
      fragmentInputs: Map[String, UIFragmentObjectDefinition]
  ) {
    // skipping exceptionHandlerFactory
    val allDefinitions: Map[String, UIObjectDefinition] = services ++ sourceFactories ++ sinkFactories ++
      customStreamTransformers ++ globalVariables ++ fragmentInputs.mapValuesNow(_.toUIObjectDefinition)
  }

  @JsonCodec(encodeOnly = true) final case class UIClazzDefinition(
      clazzName: TypingResult,
      methods: Map[String, UIMethodInfo],
      staticMethods: Map[String, UIMethodInfo]
  )

  @JsonCodec(encodeOnly = true) final case class UIMethodInfo(
      parameters: List[UIBasicParameter],
      refClazz: TypingResult,
      description: Option[String],
      varArgs: Boolean
  )

  @JsonCodec(encodeOnly = true) final case class UIBasicParameter(name: String, refClazz: TypingResult)

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
      branchParam: Boolean
  ) {

    def isOptional: Boolean = !validators.contains(MandatoryParameterValidator)

  }

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

  import pl.touk.nussknacker.engine.graph.NodeDataCodec._

  object ComponentTemplate {

    def create(
        `type`: ComponentType,
        node: NodeData,
        categories: List[String],
        branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty
    ): ComponentTemplate =
      ComponentTemplate(`type`, `type`.toString, node, categories, branchParametersTemplate)

  }

  @JsonCodec(encodeOnly = true) final case class ComponentTemplate(
      `type`: ComponentType,
      label: String,
      node: NodeData,
      categories: List[String],
      branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty
  )

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
