package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentStaticDefinition
}

final case class ComponentWithStaticDefinition(
    component: ComponentDefinitionWithImplementation,
    staticDefinition: ComponentStaticDefinition
)
