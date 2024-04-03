package pl.touk.nussknacker.engine.flink.table.aggregate

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.functions.{FlatMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.table.api.Expressions.{$, call}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.VariableConstants.KeyVariableName
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation
}
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregation.{
  aggregateByInternalColumnName,
  groupByInternalColumnName
}
import pl.touk.nussknacker.engine.flink.table.utils.NestedRowConversions.ColumnFlinkSchema
import pl.touk.nussknacker.engine.flink.table.utils.TableTypeConversions.getFlinkTypeForNuTypeOrThrow
import pl.touk.nussknacker.engine.flink.table.utils.{NestedRowConversions, RowConversions}

object TableAggregation {
  private val aggregateByInternalColumnName = "aggregateByInternalColumn"
  private val groupByInternalColumnName     = "groupByInternalColumn"
}

class TableAggregation(
    groupByLazyParam: LazyParameter[AnyRef],
    aggregateByLazyParam: LazyParameter[AnyRef],
    selectedAggregator: TableAggregator,
    nodeId: NodeId
) extends FlinkCustomStreamTransformation
    with Serializable {

  override def transform(
      start: DataStream[Context],
      context: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[AnyRef]] = {
    val env = start.getExecutionEnvironment
    // Setting batch mode to enable global window operations. If source is unbounded it will throw a runtime exception
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val tableEnv = StreamTableEnvironment.create(env)

    val streamOfRows = start.flatMap(new LazyInterpreterFunction(groupByLazyParam, aggregateByLazyParam, context))

    val groupByFlinkType     = getFlinkTypeForNuTypeOrThrow(groupByLazyParam.returnType)
    val aggregateByFlinkType = getFlinkTypeForNuTypeOrThrow(aggregateByLazyParam.returnType)

    val inputParametersTable = NestedRowConversions.buildTableFromRowStream(
      tableEnv = tableEnv,
      streamOfRows = streamOfRows,
      columnSchema = List(
        ColumnFlinkSchema(groupByInternalColumnName, groupByFlinkType),
        ColumnFlinkSchema(aggregateByInternalColumnName, aggregateByFlinkType)
      )
    )

    val groupedTable = inputParametersTable
      .groupBy($(groupByInternalColumnName))
      .select(
        $(groupByInternalColumnName),
        call(selectedAggregator.flinkFunctionName, $(aggregateByInternalColumnName)).as(aggregateByInternalColumnName)
      )

    val groupedStream: DataStream[Row] = tableEnv.toDataStream(groupedTable)

    groupedStream
      .map(RowConversions.rowToMap)
      .returns(classOf[java.util.Map[String, Any]])
      .process(new AggregateResultContextFunction(context.convertToEngineRuntimeContext))
  }

  private class AggregateResultContextFunction(
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
  ) extends ProcessFunction[java.util.Map[String, Any], ValueWithContext[AnyRef]] {
    @transient
    private var contextIdGenerator: ContextIdGenerator = _

    override def open(configuration: Configuration): Unit = {
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.toString)
    }

    override def processElement(
        value: java.util.Map[String, Any],
        ctx: ProcessFunction[java.util.Map[String, Any], ValueWithContext[AnyRef]]#Context,
        out: Collector[ValueWithContext[AnyRef]]
    ): Unit = {
      val aggregateResultValue = value.get(aggregateByInternalColumnName).asInstanceOf[AnyRef]
      val groupedByValue       = value.get(groupByInternalColumnName)
      val ctx = api.Context(contextIdGenerator.nextContextId()).withVariable(KeyVariableName, groupedByValue)
      val valueWithContext = ValueWithContext(aggregateResultValue, ctx)
      out.collect(valueWithContext)
    }

  }

  private class LazyInterpreterFunction(
      groupByParam: LazyParameter[AnyRef],
      aggregateByParam: LazyParameter[AnyRef],
      customNodeContext: FlinkCustomNodeContext
  ) extends AbstractLazyParameterInterpreterFunction(customNodeContext.lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateGroupBy          = toEvaluateFunctionConverter.toEvaluateFunction(groupByParam)
    private lazy val evaluateAggregateByParam = toEvaluateFunctionConverter.toEvaluateFunction(aggregateByParam)

    override def flatMap(context: Context, out: Collector[Row]): Unit = {
      collectHandlingErrors(context, out) {
        val evaluatedGroupBy     = evaluateGroupBy(context)
        val evaluatedAggregateBy = evaluateAggregateByParam(context)

        val row = Row.withNames()
        row.setField(groupByInternalColumnName, evaluatedGroupBy)
        row.setField(aggregateByInternalColumnName, evaluatedAggregateBy)
        row
      }
    }

  }

}
