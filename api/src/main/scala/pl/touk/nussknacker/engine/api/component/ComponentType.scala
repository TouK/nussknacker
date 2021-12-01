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

  private val BaseComponents: Set[ComponentType] = Set(
    Filter, Split, Switch, Variable, MapVariable, FragmentInput, FragmentOutput
  )

  def isBaseComponent(componentType: ComponentType): Boolean =
    BaseComponents.contains(componentType)

  def fromNodeData(nodeData: NodeData): Option[component.ComponentType.Value] = nodeData match {
    case _: Source => Some(Source)
    case _: Sink => Some(Sink)
    case _: Filter => Some(Filter)
    case _: Split => Some(Split)
    case _: Switch => Some(Switch)
    case _: Variable => Some(Variable)
    case _: VariableBuilder => Some(MapVariable)
    case _: CustomNodeData => Some(CustomNode)
    case _: Enricher => Some(Enricher)
    case _: Processor => Some(Processor)
    case _: SubprocessInput => Some(Fragments)
    case _: SubprocessInputDefinition => Some(FragmentInput)
    case _: SubprocessOutputDefinition => Some(FragmentOutput)
    case _ => None
  }
}
