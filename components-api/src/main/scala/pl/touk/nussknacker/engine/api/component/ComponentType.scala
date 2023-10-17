package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}

//It's temporary solutions in future it should be part of designer module
object ComponentType extends Enumeration {

  implicit val typeEncoder: Encoder[ComponentType.Value] = Encoder.encodeEnumeration(ComponentType)
  implicit val typeDecoder: Decoder[ComponentType.Value] = Decoder.decodeEnumeration(ComponentType)

  type ComponentType = Value

  // Basic's component types
  val Filter: Value      = Value("filter")
  val Split: Value       = Value("split")
  val Switch: Value      = Value("switch")
  val Variable: Value    = Value("variable")
  val MapVariable: Value = Value("mapVariable")

  // Generic's component types
  // TODO: we should have only Service ComponentType instead of both Processor and Enricher. We don't need to support
  //       both processor and enricher with the same name and current state causes a lot of problems
  //       (See e.g ProcessDefinitionExtractor.serviceComponentType and dumbComponentIdProvider inside AllowedProcessingModesExtractor)
  val Processor: Value  = Value("processor")
  val Enricher: Value   = Value("enricher")
  val Sink: Value       = Value("sink")
  val Source: Value     = Value("source")
  val Fragments: Value  = Value("fragments")
  val CustomNode: Value = Value("customNode")

  // Fragment's component types
  val FragmentInput: Value  = Value("input")
  val FragmentOutput: Value = Value("output")

  private val BaseComponents: Set[ComponentType] = Set(
    Filter,
    Split,
    Switch,
    Variable,
    MapVariable,
    FragmentInput,
    FragmentOutput
  )

  def isBaseComponent(componentType: ComponentType): Boolean =
    BaseComponents.contains(componentType)

}
