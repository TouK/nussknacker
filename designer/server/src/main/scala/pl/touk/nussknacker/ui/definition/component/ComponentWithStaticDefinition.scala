package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithLogic, ComponentStaticDefinition}

final case class ComponentWithStaticDefinition(
    component: ComponentDefinitionWithLogic,
    staticDefinition: ComponentStaticDefinition
)
