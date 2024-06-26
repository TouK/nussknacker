package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.collection.immutable.ListMap

class RowToTestDataConverter(classLoaderForEncoder: ClassLoader) {

  private val encoder = new BestEffortJsonEncoder(failOnUnknown = false, classLoader = classLoaderForEncoder)

  def rowsToTestData(rows: List[Row], tableDefinition: TableDefinition): TestData = TestData(rows.map(row => {
    val rowList    = tableDefinition.columns.map(_.columnName).map(colName => colName -> row.getField(colName))
    val orderedMap = ListMap(rowList: _*)
    TestRecord(encoder.encode(orderedMap))
  }))

}
