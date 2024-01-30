package pl.touk.nussknacker.ui.config.scenariotoolbar

import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarConditionType.ToolbarConditionType

object ToolbarConditionType extends Enumeration {

  type ToolbarConditionType = Value

  val OneOf: Value = Value("oneof")
  val AllOf: Value = Value("allof")

  def isAllOf(`type`: ToolbarConditionType): Boolean =
    `type`.equals(AllOf)
}

final case class ToolbarCondition(
    fragment: Option[Boolean],
    archived: Option[Boolean],
    `type`: Option[ToolbarConditionType]
) {
  def shouldMatchAllOfConditions: Boolean = `type`.exists(ToolbarConditionType.isAllOf)
}
