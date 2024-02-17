package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, ComponentWithRuntimeLogicFactory}

final case class ComponentWithStaticDefinition(
    component: ComponentWithRuntimeLogicFactory,
    staticDefinition: ComponentStaticDefinition
)
