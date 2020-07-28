package pl.touk.nussknacker.restmodel

import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveDecoder
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.TypeInfos.MethodInfo
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType

package object definition {

  @JsonCodec(encodeOnly = true) case class UIProcessObjects(nodesToAdd: List[NodeGroup],
                                                            processDefinition: UIProcessDefinition,
                                                            nodesConfig: Map[String, SingleNodeConfig],
                                                            additionalPropertiesConfig: Map[String, UiAdditionalPropertyConfig],
                                                            edgesForNodes: List[NodeEdges],
                                                            defaultAsyncInterpretation: Boolean)

  @JsonCodec(encodeOnly = true) case class UIProcessDefinition(services: Map[String, UIObjectDefinition],
                                                               sourceFactories: Map[String, UIObjectDefinition],
                                                               sinkFactories: Map[String, UIObjectDefinition],
                                                               customStreamTransformers: Map[String, UIObjectDefinition],
                                                               signalsWithTransformers: Map[String, UIObjectDefinition],
                                                               exceptionHandlerFactory: UIObjectDefinition,
                                                               globalVariables: Map[String, UIObjectDefinition],
                                                               typesInformation: Set[UIClazzDefinition],
                                                               subprocessInputs: Map[String, UIObjectDefinition]) {
    // skipping exceptionHandlerFactory
    val allDefinitions: Map[String, UIObjectDefinition] = services ++ sourceFactories ++ sinkFactories ++
      customStreamTransformers ++ signalsWithTransformers ++ globalVariables ++ subprocessInputs
  }

  @JsonCodec(encodeOnly = true) case class UIClazzDefinition(clazzName: TypingResult, methods: Map[String, MethodInfo])

  @JsonCodec(encodeOnly = true) case class UIParameter(name: String,
                                                       typ: TypingResult,
                                                       editor: ParameterEditor,
                                                       validators: List[ParameterValidator],
                                                       additionalVariables: Map[String, TypingResult],
                                                       branchParam: Boolean) {

    def isOptional: Boolean = !validators.contains(MandatoryParameterValidator)

  }

  @JsonCodec(encodeOnly = true) case class UIObjectDefinition(parameters: List[UIParameter],
                                                              returnType: Option[TypingResult],
                                                              categories: List[String],
                                                              nodeConfig: SingleNodeConfig) {

    def hasNoReturn: Boolean = returnType.isEmpty

  }

  @JsonCodec case class NodeTypeId(`type`: String, id: Option[String] = None)

  @JsonCodec case class NodeEdges(nodeId: NodeTypeId, edges: List[EdgeType], canChooseNodes: Boolean, isForInputDefinition: Boolean)

  import pl.touk.nussknacker.engine.graph.NodeDataCodec._
  @JsonCodec(encodeOnly = true) case class NodeToAdd(`type`: String, label: String, node: NodeData, categories: List[String], branchParametersTemplate: List[evaluatedparam.Parameter] = List.empty)

  @JsonCodec(encodeOnly = true) case class NodeGroup(name: String, possibleNodes: List[NodeToAdd])

  @JsonCodec case class UiAdditionalPropertyConfig(defaultValue: Option[String],
                                                   editor: ParameterEditor,
                                                   validators: List[ParameterValidator],
                                                   label: Option[String])

  object UIParameter {

    implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[UIParameter] = deriveDecoder[UIParameter]

  }

}
