package pl.touk.nussknacker.engine.flink.table.aggregate

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object Aggregator {
  val allAggregators: List[Aggregator] = List(Sum, First)
}

sealed trait Aggregator {
  val name: String
  val inputTypeConstraint: Option[TypingResult]
  def outputType(inputTypeInRuntime: TypingResult): TypingResult
  val flinkFunctionName: String
}

case object Sum extends Aggregator {
  override val name: String = "Sum"
  // TODO local: is Typed[Number] ok?
  override val inputTypeConstraint: Option[TypingResult]                  = Some(Typed[Number])
  override def outputType(inputTypeInRuntime: TypingResult): TypingResult = Typed[Number]
  override val flinkFunctionName: String                                  = "sum"
}

case object First extends Aggregator {
  override val name: String                                               = "First"
  override val inputTypeConstraint: Option[TypingResult]                  = None
  override def outputType(inputTypeInRuntime: TypingResult): TypingResult = inputTypeInRuntime
  override val flinkFunctionName: String                                  = "first_value"
}
