package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition

case class ModelDefinitionWithComponentIds[T](
    components: List[(ComponentIdWithName, T)],
    expressionConfig: ExpressionConfigDefinition[T],
    settings: ClassExtractionSettings
)

case class ComponentIdWithName(id: ComponentId, name: String)
