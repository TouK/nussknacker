package pl.touk.nussknacker.engine.api.context.transformation

sealed trait NodeDependencyValue

case class OutputVariableNameValue(name: String) extends NodeDependencyValue
case class TypedNodeDependencyValue(value: Any) extends NodeDependencyValue
