package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.DataTypes
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory

// TODO: move this to testkit after table api components exit experimental stage
object TestTableComponents {

  val oneRecordTableSourceName = "oneRecordTableSource"
  val oneRecordTableName       = "oneRecordTable"
  private val testColumnName   = "stubCol"

  // Source producing a single record is useful for tests that require the record to trigger evaluation
  private def tableDefs(tableName: String) = TableSqlDefinitions(
    tableDefinitions = List(
      TableDefinition(
        tableName = tableName,
        typingResult = typing.Typed.record(Map(testColumnName -> Typed[Int])),
        columns = List(
          ColumnDefinition(columnName = testColumnName, typingResult = Typed[Int], flinkDataType = DataTypes.INT())
        )
      )
    ),
    sqlStatements = List(
      s"CREATE TABLE $tableName ($testColumnName INT) WITH ('connector' = 'datagen', 'number-of-rows' = '1')"
    )
  )

  val singleRecordBatchTable: ComponentDefinition =
    ComponentDefinition(
      oneRecordTableSourceName,
      new TableSourceFactory(
        tableDefs(oneRecordTableName),
        enableFlinkBatchExecutionMode = true,
        testDataGenerationMode = TestDataGenerationMode.Live
      )
    )

}
