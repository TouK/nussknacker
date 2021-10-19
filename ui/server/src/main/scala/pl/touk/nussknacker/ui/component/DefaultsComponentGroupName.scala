package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentType}

object DefaultsComponentGroupName {
  val BaseGroupName: ComponentGroupName = ComponentGroupName("base")
  val ServicesGroupName: ComponentGroupName = ComponentGroupName("services")
  val EnrichersGroupName: ComponentGroupName = ComponentGroupName("enrichers")
  val CustomGroupName: ComponentGroupName = ComponentGroupName("custom")
  val OptionalEndingCustomGroupName: ComponentGroupName = ComponentGroupName("optionalEndingCustom")
  val SinksGroupName: ComponentGroupName = ComponentGroupName("sinks")
  val SourcesGroupName: ComponentGroupName = ComponentGroupName("sources")
  val FragmentsGroupName: ComponentGroupName = ComponentGroupName("fragments")
  val FragmentsDefinitionGroupName: ComponentGroupName = ComponentGroupName("fragmentDefinition")

  def fromComponentType(componentType: ComponentType, optional: Boolean = false): ComponentGroupName = componentType match {
    case ComponentType.Filter | ComponentType.Split | ComponentType.Switch | ComponentType.Variable | ComponentType.MapVariable => BaseGroupName
    case ComponentType.Processor => ServicesGroupName
    case ComponentType.Enricher => EnrichersGroupName
    case ComponentType.Source => SourcesGroupName
    case ComponentType.Sink => SinksGroupName
    case ComponentType.Fragments => FragmentsGroupName
    case ComponentType.FragmentInput | ComponentType.FragmentOutput => FragmentsDefinitionGroupName
    case ComponentType.CustomNode if optional => OptionalEndingCustomGroupName
    case ComponentType.CustomNode if !optional => CustomGroupName
  }
}
