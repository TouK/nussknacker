package pl.touk.nussknacker.engine.flink.table.source;

import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.IsEqualToFieldRule
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.source.TableSource._
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions

class TableSource(tableDefinition: TableDefinition, sqlStatements: List[SqlStatement]) extends FlinkSource {

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    val tableEnv = StreamTableEnvironment.create(env);

    sqlStatements.foreach(tableEnv.executeSql)

    val selectQuery = tableEnv.from(tableDefinition.tableName)

    val finalQuery = flinkNodeContext.nodeEventsFilteringRules.fieldRules.toList
      .map { case (fieldName, IsEqualToFieldRule(expectedValue)) =>
        $(fieldName.value).isEqual(expectedValue)
      }
      .reduceOption { (expr1, expr2) => expr1.and(expr2) }
      .map(selectQuery.filter)
      .getOrElse(selectQuery)

    val streamOfRows: DataStream[Row] = tableEnv.toDataStream(finalQuery)

    val streamOfMaps = streamOfRows
      .map(RowConversions.rowToMap)
      .returns(classOf[RECORD])

    val contextStream = streamOfMaps.map(
      new FlinkContextInitializingFunction(
        contextInitializer,
        flinkNodeContext.nodeId,
        flinkNodeContext.convertToEngineRuntimeContext
      ),
      flinkNodeContext.contextTypeInfo
    )

    contextStream
  }

}

object TableSource {

  type RECORD = java.util.Map[String, Any]

  // this context initialization was copied from kafka source
  val contextInitializer: ContextInitializer[RECORD] = new BasicContextInitializer[RECORD](Typed[RECORD])

  class FlinkContextInitializingFunction[T](
      contextInitializer: ContextInitializer[T],
      nodeId: String,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
  ) extends RichMapFunction[T, Context] {

    private var initializingStrategy: ContextInitializingFunction[T] = _

    override def open(parameters: Configuration): Unit = {
      val contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
      initializingStrategy = contextInitializer.initContext(contextIdGenerator)
    }

    override def map(input: T): Context = {
      initializingStrategy(input)
    }

  }

}
