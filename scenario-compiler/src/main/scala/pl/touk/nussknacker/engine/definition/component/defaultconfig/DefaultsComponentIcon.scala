package pl.touk.nussknacker.engine.definition.component.defaultconfig

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

object DefaultsComponentIcon {
  // Warning: In case if component's definition is missing for a node, we have implicit contract that these icon url's
  // should be the same as node names in scenario-api - see getIconFromDef in ComponentIcon.tsx
  // TODO: We should return some "unknown" icon in case when definition is missing, then we can introduce convention that icon = component name
  val SourceIcon          = "/assets/components/Source.svg"
  val SinkIcon            = "/assets/components/Sink.svg"
  val EnricherIcon        = "/assets/components/Enricher.svg"
  val ServiceIcon         = "/assets/components/Processor.svg"
  val CustomComponentIcon = "/assets/components/CustomNode.svg"
  val FragmentIcon        = "/assets/components/FragmentInput.svg"

  val FilterIcon                   = "/assets/components/Filter.svg"
  val SplitIcon                    = "/assets/components/Split.svg"
  val ChoiceIcon                   = "/assets/components/Switch.svg"
  val VariableIcon                 = "/assets/components/Variable.svg"
  val RecordVariableIcon           = "/assets/components/VariableBuilder.svg"
  val FragmentInputDefinitionIcon  = "/assets/components/FragmentInputDefinition.svg"
  val FragmentOutputDefinitionIcon = "/assets/components/FragmentOutputDefinition.svg"

  def fromComponentId(componentId: ComponentId, isEnricher: Option[Boolean]): String = {
    forNotBuiltInComponentType.lift((componentId.`type`, isEnricher)).getOrElse(forBuiltInComponent(componentId))
  }

  def forNotBuiltInComponentType: PartialFunction[(ComponentType, Option[Boolean]), String] = {
    case (ComponentType.Source, _)           => SourceIcon
    case (ComponentType.Sink, _)             => SinkIcon
    case (ComponentType.Service, Some(true)) => EnricherIcon
    case (ComponentType.Service, _)          => ServiceIcon
    case (ComponentType.CustomComponent, _)  => CustomComponentIcon
    case (ComponentType.Fragment, _)         => FragmentIcon
  }

  def forBuiltInComponent(componentId: ComponentId): String = componentId match {
    case BuiltInComponentId.Filter                   => FilterIcon
    case BuiltInComponentId.Split                    => SplitIcon
    case BuiltInComponentId.Choice                   => ChoiceIcon
    case BuiltInComponentId.Variable                 => VariableIcon
    case BuiltInComponentId.RecordVariable           => RecordVariableIcon
    case BuiltInComponentId.FragmentInputDefinition  => FragmentInputDefinitionIcon
    case BuiltInComponentId.FragmentOutputDefinition => FragmentOutputDefinitionIcon
    case _ => throw new IllegalStateException(s"Icon mapping for built-in component [$componentId] not defined")
  }

}
