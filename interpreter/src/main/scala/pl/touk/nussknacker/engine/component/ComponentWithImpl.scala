package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.Component

// TODO: rename to Component after Component -> ComponentDefinition
case class ComponentWithImpl(definition: Component, implementationProvider: RuntimeImplementationProvider)