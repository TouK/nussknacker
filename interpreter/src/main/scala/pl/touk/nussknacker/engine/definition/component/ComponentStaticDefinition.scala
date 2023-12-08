package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

case class ComponentStaticDefinition(
    componentType: ComponentType,
    parameters: List[Parameter],
    returnType: Option[TypingResult],
    categories: Option[List[String]],
    componentConfig: SingleComponentConfig,
    componentTypeSpecificData: ComponentTypeSpecificData
) {

  def withComponentConfig(componentConfig: SingleComponentConfig): ComponentStaticDefinition =
    copy(componentConfig = componentConfig)

  val hasNoReturn: Boolean = returnType.isEmpty

}

sealed trait ComponentTypeSpecificData

case object NoComponentTypeSpecificData extends ComponentTypeSpecificData

case class CustomComponentSpecificData(manyInputs: Boolean, canBeEnding: Boolean) extends ComponentTypeSpecificData

object ComponentTypeSpecificData {

  implicit class ComponentTypeSpecificDataCaster(typeSpecificData: ComponentTypeSpecificData) {
    def asCustomComponentData: CustomComponentSpecificData = typeSpecificData.asInstanceOf[CustomComponentSpecificData]
  }

}
