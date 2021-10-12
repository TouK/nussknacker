package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentGroupName

object DefaultsComponentGroupName {
  val Base: ComponentGroupName = ComponentGroupName("base")
  val Services: ComponentGroupName = ComponentGroupName("services")
  val Enrichers: ComponentGroupName = ComponentGroupName("enrichers")
  val Custom: ComponentGroupName = ComponentGroupName("custom")
  val OptionalEndingCustom: ComponentGroupName = ComponentGroupName("optionalEndingCustom")
  val Sinks: ComponentGroupName = ComponentGroupName("sinks")
  val Sources: ComponentGroupName = ComponentGroupName("sources")
  val Fragments: ComponentGroupName = ComponentGroupName("fragments")
  val FragmentsDefinition: ComponentGroupName = ComponentGroupName("fragmentDefinition")
}
