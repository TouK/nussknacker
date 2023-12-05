package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo, ComponentType}

private[component] object DefaultsComponentIcon {
  val FilterIcon = "/assets/components/Filter.svg"
  val SplitIcon  = "/assets/components/Split.svg"
  // TODO: rename asset
  val ChoiceIcon   = "/assets/components/Switch.svg"
  val VariableIcon = "/assets/components/Variable.svg"
  // TODO: rename asset
  val RecordVariableIcon           = "/assets/components/VariableBuilder.svg"
  val ProcessorIcon                = "/assets/components/Processor.svg"
  val EnricherIcon                 = "/assets/components/Enricher.svg"
  val SinkIcon                     = "/assets/components/Sink.svg"
  val SourceIcon                   = "/assets/components/Source.svg"
  val FragmentIcon                 = "/assets/components/FragmentInput.svg"
  val CustomComponentIcon          = "/assets/components/CustomNode.svg"
  val FragmentInputDefinitionIcon  = "/assets/components/FragmentInputDefinition.svg"
  val FragmentOutputDefinitionIcon = "/assets/components/FragmentOutputDefinition.svg"

  def fromComponentInfo(componentInfo: ComponentInfo, isEnricher: Option[Boolean]): String = {
    forNotBuiltInComponentType.lift((componentInfo.`type`, isEnricher)).getOrElse(forBuiltInComponent(componentInfo))
  }

  private[component] def forNotBuiltInComponentType: PartialFunction[(ComponentType, Option[Boolean]), String] = {
    case (ComponentType.Source, _)           => SourceIcon
    case (ComponentType.Sink, _)             => SinkIcon
    case (ComponentType.Service, Some(true)) => EnricherIcon
    case (ComponentType.Service, _)          => ProcessorIcon
    case (ComponentType.CustomComponent, _)  => CustomComponentIcon
    case (ComponentType.Fragment, _)         => FragmentIcon
  }

  private[component] def forBuiltInComponent(componentInfo: ComponentInfo) = componentInfo match {
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
