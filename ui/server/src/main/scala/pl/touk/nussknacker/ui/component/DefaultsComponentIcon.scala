package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

object DefaultsComponentIcon {
  val FilterIcon = "/assets/components/Filter.svg"
  val SplitIcon = "/assets/components/Split.svg"
  val SwitchIcon = "/assets/components/Switch.svg"
  val VariableIcon = "/assets/components/Variable.svg"
  val MapVariableIcon = "/assets/components/VariableBuilder.svg"
  val ProcessorIcon = "/assets/components/Processor.svg"
  val EnricherIcon = "/assets/components/Enricher.svg"
  val SinkIcon = "/assets/components/Sink.svg"
  val SourceIcon = "/assets/components/Source.svg"
  val FragmentsIcon = "/assets/components/SubprocessInput.svg"
  val CustomNodeIcon = "/assets/components/CustomNode.svg"
  val FragmentInputIcon = "/assets/components/SubprocessInputDefinition.svg"
  val FragmentOutputIcon = "/assets/components/SubprocessOutputDefinition.svg"

  def fromComponentType(componentType: ComponentType): String = componentType match {
    case ComponentType.Filter => FilterIcon
    case ComponentType.Split => SplitIcon
    case ComponentType.Switch => SwitchIcon
    case ComponentType.Variable => VariableIcon
    case ComponentType.MapVariable => MapVariableIcon
    case ComponentType.Processor => ProcessorIcon
    case ComponentType.Enricher => EnricherIcon
    case ComponentType.Sink => SinkIcon
    case ComponentType.Source => SourceIcon
    case ComponentType.Fragments => FragmentsIcon
    case ComponentType.CustomNode => CustomNodeIcon
    case ComponentType.FragmentInput => FragmentInputIcon
    case ComponentType.FragmentOutput => FragmentOutputIcon
  }
}
