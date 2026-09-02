package pl.touk.nussknacker.engine.definition.component

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.component.{ComponentOutput, ComponentType}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

sealed trait ComponentTypeSpecificData {
  def componentType: ComponentType
}

case object SourceSpecificData extends ComponentTypeSpecificData {
  override def componentType: ComponentType = ComponentType.Source
}

case object SinkSpecificData extends ComponentTypeSpecificData {
  override def componentType: ComponentType = ComponentType.Sink
}

case object ServiceSpecificData extends ComponentTypeSpecificData {
  override def componentType: ComponentType = ComponentType.Service
}

object CustomComponentSpecificData {

  def apply(canHaveManyInputs: Boolean, canBeEnding: Boolean): CustomComponentSpecificData =
    CustomComponentSpecificData(
      canHaveManyInputs,
      canBeEnding,
      outputs = NonEmptyList.of(ComponentOutput.MainOutput)
    )

}

final case class CustomComponentSpecificData(
    canHaveManyInputs: Boolean,
    canBeEnding: Boolean,
    outputs: NonEmptyList[ComponentOutput]
) extends ComponentTypeSpecificData {
  override def componentType: ComponentType = ComponentType.CustomComponent
}

case object BuiltInComponentSpecificData extends ComponentTypeSpecificData {
  override def componentType: ComponentType = ComponentType.BuiltIn
}

case class FragmentSpecificData(outputNames: List[String]) extends ComponentTypeSpecificData {
  override def componentType: ComponentType = ComponentType.Fragment
}

object ComponentTypeSpecificData {

  implicit class ComponentTypeSpecificDataCaster(typeSpecificData: ComponentTypeSpecificData) {
    def asCustomComponentData: CustomComponentSpecificData = typeSpecificData.asInstanceOf[CustomComponentSpecificData]
  }

}
