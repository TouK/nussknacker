package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.catalog.{Column, ResolvedSchema}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
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
        ResolvedSchema.of(Column.physical(testColumnName, DataTypes.INT()))
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
        enableFlinkBatchExecutionMode = false,
        testDataGenerationMode = TestDataGenerationMode.Live
      )
    )

}
