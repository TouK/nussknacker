package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, ComponentWithDefinition}

final case class ComponentWithStaticDefinition(
    component: ComponentWithDefinition,
    staticDefinition: ComponentStaticDefinition
)
