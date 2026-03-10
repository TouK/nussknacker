package pl.touk.nussknacker.engine.flink.table.io.definition.validation

import cats.data.{NonEmptyList, Validated}
import org.apache.flink.table.api.TableEnvironment
import pl.touk.nussknacker.engine.flink.table.io.definition._

trait TableUsageValidator {

  def validateTableUsage(
      table: TableDefinition,
      tableUseCase: TableUseCase,
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition
  ): Validated[NonEmptyList[FlinkDataDefinitionError], Unit]

}

sealed trait TableUseCase

object TableUseCase {
  final case object Source extends TableUseCase
  final case object Sink   extends TableUseCase
}
