package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.ComponentType
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

final case class CustomComponentSpecificData(canHaveManyInputs: Boolean, canBeEnding: Boolean)
    extends ComponentTypeSpecificData {
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
