package pl.touk.nussknacker.engine.definition.component.defaultconfig

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo, ComponentType}

object DefaultsComponentIcon {
  val SourceIcon   = "/assets/components/Source.svg"
  val SinkIcon     = "/assets/components/Sink.svg"
  val EnricherIcon = "/assets/components/Enricher.svg"
  // TODO: rename file + icon = component group convention instead of code
  val ServiceIcon         = "/assets/components/Processor.svg"
  val CustomComponentIcon = "/assets/components/CustomNode.svg"
  val FragmentIcon        = "/assets/components/FragmentInput.svg"

  // Warning: we have implicit contract that these icon url's should be the same as node names in scenario-api.
  // TODO: We should base on component's definition on FE instead, thanks to that we could introduce icon = component name convention instead of code
  val FilterIcon                   = "/assets/components/Filter.svg"
  val SplitIcon                    = "/assets/components/Split.svg"
  val ChoiceIcon                   = "/assets/components/Switch.svg"
  val VariableIcon                 = "/assets/components/Variable.svg"
  val RecordVariableIcon           = "/assets/components/VariableBuilder.svg"
  val FragmentInputDefinitionIcon  = "/assets/components/FragmentInputDefinition.svg"
  val FragmentOutputDefinitionIcon = "/assets/components/FragmentOutputDefinition.svg"

  def fromComponentInfo(componentInfo: ComponentInfo, isEnricher: Option[Boolean]): String = {
    forNotBuiltInComponentType.lift((componentInfo.`type`, isEnricher)).getOrElse(forBuiltInComponent(componentInfo))
  }

  def forNotBuiltInComponentType: PartialFunction[(ComponentType, Option[Boolean]), String] = {
    case (ComponentType.Source, _)           => SourceIcon
    case (ComponentType.Sink, _)             => SinkIcon
    case (ComponentType.Service, Some(true)) => EnricherIcon
    case (ComponentType.Service, _)          => ServiceIcon
    case (ComponentType.CustomComponent, _)  => CustomComponentIcon
    case (ComponentType.Fragment, _)         => FragmentIcon
  }

  // TODO: convention icon = component name
  def forBuiltInComponent(componentInfo: ComponentInfo): String = componentInfo match {
    case BuiltInComponentInfo.Filter                   => FilterIcon
    case BuiltInComponentInfo.Split                    => SplitIcon
    case BuiltInComponentInfo.Choice                   => ChoiceIcon
    case BuiltInComponentInfo.Variable                 => VariableIcon
    case BuiltInComponentInfo.RecordVariable           => RecordVariableIcon
    case BuiltInComponentInfo.FragmentInputDefinition  => FragmentInputDefinitionIcon
    case BuiltInComponentInfo.FragmentOutputDefinition => FragmentOutputDefinitionIcon
    case _ => throw new IllegalStateException(s"Icon mapping for built-in component [$componentInfo] not defined")
  }

}
