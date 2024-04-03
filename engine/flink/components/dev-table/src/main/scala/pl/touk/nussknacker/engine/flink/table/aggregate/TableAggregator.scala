package pl.touk.nussknacker.engine.flink.table.aggregate

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import enumeratum._
import org.apache.flink.table.functions.BuiltInFunctionDefinitions

// TODO: unify aggregator function definitions with unbounded-streaming ones. Current duplication may lead to
//  inconsistency in naming and may be confusing for users
// TODO: add remaining aggregators
object TableAggregator extends Enum[TableAggregator] {
  val values = findValues

  case object Sum extends TableAggregator {
    override val name: String                                               = "Sum"
    override val inputTypeConstraint: Option[TypingResult]                  = Some(Typed[Number])
    override def outputType(inputTypeInRuntime: TypingResult): TypingResult = Typed[Number]
    override val flinkFunctionName: String                                  = BuiltInFunctionDefinitions.SUM.getName
  }

  case object First extends TableAggregator {
    override val name: String                                               = "First"
    override val inputTypeConstraint: Option[TypingResult]                  = None
    override def outputType(inputTypeInRuntime: TypingResult): TypingResult = inputTypeInRuntime
    override val flinkFunctionName: String = BuiltInFunctionDefinitions.FIRST_VALUE.getName
  }

}

sealed trait TableAggregator extends EnumEntry {
  val name: String
  val inputTypeConstraint: Option[TypingResult]
  def outputType(inputTypeInRuntime: TypingResult): TypingResult
  val flinkFunctionName: String
}
