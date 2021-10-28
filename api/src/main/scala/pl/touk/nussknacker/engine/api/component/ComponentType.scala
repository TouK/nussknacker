package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.component
import pl.touk.nussknacker.engine.graph.node.{BranchEndData, CustomNodeData, Enricher, Filter, NodeData, Processor, Sink, Source, Split, SubprocessInput, SubprocessInputDefinition, SubprocessOutput, SubprocessOutputDefinition, Switch, Variable, VariableBuilder}

//It's temporary solutions in future it should be part of ui module
object ComponentType extends Enumeration {

  implicit val typeEncoder: Encoder[ComponentType.Value] = Encoder.encodeEnumeration(ComponentType)
  implicit val typeDecoder: Decoder[ComponentType.Value] = Decoder.decodeEnumeration(ComponentType)

  type ComponentType = Value

  //Basic's component types
  val Filter: Value = Value("filter")
  val Split: Value = Value("split")
  val Switch: Value = Value("switch")
  val Variable: Value = Value("variable")
  val MapVariable: Value = Value("mapVariable")

  //Generic's component types
  val Processor: Value = Value("processor")
  val Enricher: Value = Value("enricher")
  val Sink: Value = Value("sink")
  val Source: Value = Value("source")
  val Fragments: Value = Value("fragments")
  val CustomNode: Value = Value("customNode")

  //Fragment's component types
  val FragmentInput: Value = Value("input")
  val FragmentOutput: Value = Value("output")

  //It's temporary solution until we figure how to provide unique component id
  val BranchEnd: Value = Value("branchEnd")

  private val BaseComponents: Set[ComponentType] = Set(
    Filter, Split, Switch, Variable, MapVariable, FragmentInput, FragmentOutput
  )

  def isBaseComponent(componentType: ComponentType): Boolean =
    BaseComponents.contains(componentType)

  def fromNodeData(nodeData: NodeData): component.ComponentType.Value = nodeData match {
    case _: Source => Source
    case _: Sink => Sink
    case _: Filter => Filter
    case _: Split => Split
    case _: Switch => Switch
    case _: Variable => Variable
    case _: VariableBuilder => MapVariable
    case _: CustomNodeData => CustomNode
    case _: Enricher => Enricher
    case _: Processor => Processor
    case _: SubprocessInput => Fragments
    case _: SubprocessOutput => Fragments
    case _: SubprocessInputDefinition => FragmentInput
    case _: SubprocessOutputDefinition => FragmentOutput
    case _: BranchEndData => BranchEnd
  }
}
