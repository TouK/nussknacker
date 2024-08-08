package pl.touk.nussknacker.engine.flink.table

import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory

// TODO: move this to testkit after table api components exit experimental stage
object TestTableComponents {

  val oneRecordTableSourceName = "oneRecordTableSource"
  val oneRecordTableName       = "oneRecordTable"
  private val testColumnName   = "stubCol"

  val singleRecordBatchTable: ComponentDefinition =
    ComponentDefinition(
      oneRecordTableSourceName,
      new TableSourceFactory(
        sqlStatements = List(
          s"CREATE TABLE $oneRecordTableName ($testColumnName INT) WITH ('connector' = 'datagen', 'number-of-rows' = '1')"
        ),
        testDataGenerationMode = TestDataGenerationMode.Live
      )
    )

}
