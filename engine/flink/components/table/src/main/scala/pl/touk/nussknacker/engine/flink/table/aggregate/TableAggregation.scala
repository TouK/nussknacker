package pl.touk.nussknacker.engine.flink.table.aggregate

import org.apache.flink.api.common.functions.{FlatMapFunction, RuntimeContext}
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.table.api.Expressions.{$, call}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.VariableConstants.KeyVariableName
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
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
    val env      = start.getExecutionEnvironment
    val tableEnv = StreamTableEnvironment.create(env)

    val streamOfRows = start.flatMap(
      new LazyInterpreterFunction(groupByLazyParam, aggregateByLazyParam, context),
      Types.ROW_NAMED(
        Array(groupByInternalColumnName, aggregateByInternalColumnName),
        context.typeInformationDetection.forType(groupByLazyParam.returnType),
        context.typeInformationDetection.forType(aggregateByLazyParam.returnType)
      )
    )

    val inputParametersTable = tableEnv.fromDataStream(streamOfRows)

    val groupedTable = inputParametersTable
      .groupBy($(groupByInternalColumnName))
      .select(
        $(groupByInternalColumnName),
        call(selectedAggregator.flinkFunctionName, $(aggregateByInternalColumnName)).as(aggregateByInternalColumnName)
      )

    val groupedStream: DataStream[Row] = tableEnv.toDataStream(groupedTable)

    groupedStream
      .process(
        new AggregateResultContextFunction(context.convertToEngineRuntimeContext),
        context.typeInformationDetection.forValueWithContext(
          ValidationContext.empty.withVariableUnsafe(KeyVariableName, groupByLazyParam.returnType),
          aggregateByLazyParam.returnType
        )
      )
  }

  private class AggregateResultContextFunction(
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
  ) extends ProcessFunction[Row, ValueWithContext[AnyRef]] {

    @transient
    private var contextIdGenerator: ContextIdGenerator = _

    override def open(configuration: Configuration): Unit = {
      contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.toString)
    }

    override def processElement(
        value: Row,
        ctx: ProcessFunction[Row, ValueWithContext[AnyRef]]#Context,
        out: Collector[ValueWithContext[AnyRef]]
    ): Unit = {
      val aggregateResultValue = value.getField(aggregateByInternalColumnName)
      val groupedByValue       = value.getField(groupByInternalColumnName)
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
