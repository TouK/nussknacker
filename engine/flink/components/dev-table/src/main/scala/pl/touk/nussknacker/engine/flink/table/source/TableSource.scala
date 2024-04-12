package pl.touk.nussknacker.engine.flink.table.source;

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextInitializer}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, StandardFlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.source.TableSource._
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions

class TableSource(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    enableFlinkBatchExecutionMode: Boolean
) extends StandardFlinkSource[RECORD] {

  override def initialSourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[RECORD] = {
    // TODO: move this to flink-executor - ideally should set be near level of ExecutionConfigPreparer
    if (enableFlinkBatchExecutionMode) {
      env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    }
    val tableEnv = StreamTableEnvironment.create(env);

    sqlStatements.foreach(tableEnv.executeSql)

    val table = tableEnv.from(tableDefinition.tableName)

    val streamOfRows: DataStream[Row] = tableEnv.toDataStream(table)

    val streamOfMaps = streamOfRows
      .map(RowConversions.rowToMap)
      .returns(classOf[RECORD])

    new DataStreamSource(streamOfMaps)
  }

  override val contextInitializer: ContextInitializer[RECORD] = new BasicContextInitializer[RECORD](Typed[RECORD])

}

object TableSource {
  private type RECORD = java.util.Map[String, Any]
}
