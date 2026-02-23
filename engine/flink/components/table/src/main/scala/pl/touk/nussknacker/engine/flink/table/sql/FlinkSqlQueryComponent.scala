package pl.touk.nussknacker.engine.flink.table.sql

import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.table.sql.FlinkSqlQueryComponentFactory.SqlQuery

import QueryContextSchema.NuContextExtension

class FlinkSqlQueryComponent(query: SqlQuery, contextSchema: QueryContextSchema, nodeId: NodeId)
    extends FlinkCustomStreamTransformation
    with Serializable {

  override def transform(
      start: DataStream[Context],
      context: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[AnyRef]] = {
    val tableEnv = StreamTableEnvironment.create(start.getExecutionEnvironment)

    val rowStream = start.map(
      (ctx: NkContext) => ctx.toRow(contextSchema),
      contextSchema.rowAlignedTypeInformation
    )
    tableEnv.createTemporaryView(
      QueryContextSchema.contextTableName,
      tableEnv.fromDataStream(rowStream, contextSchema.tableSchemaWithEventTime)
    )

    val queryTable     = tableEnv.sqlQuery(query.sql)
    val outputTypeInfo = context.valueWithContextInfo.forType[AnyRef](query.outputType)

    tableEnv
      .toDataStream(queryTable)
      .map(
        new NewContextFunction(
          nodeId = nodeId,
          convertToEngineRuntimeContext = context.convertToEngineRuntimeContext
        ),
        outputTypeInfo
      )
  }

  private class NewContextFunction(
      nodeId: NodeId,
      convertToEngineRuntimeContext: org.apache.flink.api.common.functions.RuntimeContext => pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
  ) extends RichMapFunction[Row, ValueWithContext[AnyRef]] {

    @transient
    private var contextIdGenerator: ContextIdGenerator = _

    override def open(openContext: OpenContext): Unit = {
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
    }

    override def map(row: Row): ValueWithContext[AnyRef] = {
      ValueWithContext(row, NkContext(contextIdGenerator.nextContextId()))
    }

  }

}
