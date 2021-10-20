package pl.touk.nussknacker.restmodel.component

import io.circe.{Decoder, Encoder}

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

  private val baseComponents: List[ComponentType] = List(
    Filter, Split, Switch, Variable, MapVariable, FragmentInput, FragmentOutput
  )

  def isBaseComponent(componentType: ComponentType): Boolean =
    baseComponents.contains(componentType)
}
