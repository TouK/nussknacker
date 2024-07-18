package pl.touk.nussknacker.engine.definition.component.defaultconfig

import pl.touk.nussknacker.engine.api.component.ComponentGroupName

object DefaultsComponentGroupName {
  val BaseGroupName: ComponentGroupName                 = ComponentGroupName("base")
  val ServicesGroupName: ComponentGroupName             = ComponentGroupName("services")
  val EnrichersGroupName: ComponentGroupName            = ComponentGroupName("enrichers")
  val CustomGroupName: ComponentGroupName               = ComponentGroupName("custom")
  val OptionalEndingCustomGroupName: ComponentGroupName = ComponentGroupName("optionalEndingCustom")
  val SinksGroupName: ComponentGroupName                = ComponentGroupName("sinks")
  val SourcesGroupName: ComponentGroupName              = ComponentGroupName("sources")
  val FragmentsGroupName: ComponentGroupName            = ComponentGroupName("fragments")
  val FragmentsDefinitionGroupName: ComponentGroupName  = ComponentGroupName("fragmentDefinition")
  val DeprecatedGroupName: ComponentGroupName           = ComponentGroupName("deprecated")

  val allAvailableForFragment: List[ComponentGroupName] = List(
    FragmentsGroupName,
    ServicesGroupName,
    EnrichersGroupName,
    CustomGroupName,
    SinksGroupName,
    SourcesGroupName,
    DeprecatedGroupName
  )

}
