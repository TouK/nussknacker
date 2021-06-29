package pl.touk.nussknacker.ui.config.processtoolbar

import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarConditionType.ToolbarConditionType

object ToolbarConditionType extends Enumeration {

  type ToolbarConditionType = Value

  val OneOf: Value = Value("oneof")
  val AllOf: Value = Value("allof")

  def isAllOf(`type`: ToolbarConditionType): Boolean =
    `type`.equals(AllOf)
}

case class ToolbarCondition(subprocess: Option[Boolean], archived: Option[Boolean], `type`: Option[ToolbarConditionType]) {
  def shouldMatchAllOfConditions: Boolean = `type`.exists(ToolbarConditionType.isAllOf)
}
