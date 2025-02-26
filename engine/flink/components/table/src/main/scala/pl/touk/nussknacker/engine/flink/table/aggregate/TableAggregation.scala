package pl.touk.nussknacker.engine.flink.table.aggregate

import org.apache.flink.api.common.functions.{FlatMapFunction, OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.table.api.Expressions.{$, call}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.VariableConstants.KeyVariableName
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregation.{
  aggregateByInternalColumnName,
  groupByInternalColumnName
}
import pl.touk.nussknacker.engine.flink.table.utils.ToTableTypeEncoder

object TableAggregation {
  private val aggregateByInternalColumnName = "aggregateByInternalColumn"
  private val groupByInternalColumnName     = "groupByInternalColumn"
}

class TableAggregation(
    groupByLazyParam: LazyParameter[AnyRef],
    aggregateByLazyParam: LazyParameter[AnyRef],
    selectedAggregator: TableAggregator,
    aggregationResultType: TypingResult,
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
      new GroupByInputPreparingFunction(groupByLazyParam, aggregateByLazyParam, context),
      GroupByInputPreparingFunction.outputTypeInfo
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
        AggregateResultContextFunction.outputTypeInfo
      )
  }

  private class GroupByInputPreparingFunction(
      groupByParam: LazyParameter[AnyRef],
      aggregateByParam: LazyParameter[AnyRef],
      customNodeContext: FlinkCustomNodeContext
  ) extends AbstractLazyParameterInterpreterFunction(customNodeContext.lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateGroupBy          = toEvaluateFunctionConverter.toEvaluateFunction(groupByParam)
    private lazy val evaluateAggregateByParam = toEvaluateFunctionConverter.toEvaluateFunction(aggregateByParam)

    override def flatMap(context: Context, out: Collector[Row]): Unit = {
      collectHandlingErrors(context, out) {
        val evaluatedGroupBy = ToTableTypeEncoder.encode(evaluateGroupBy(context), groupByParam.returnType)
        val evaluatedAggregateBy =
          ToTableTypeEncoder.encode(evaluateAggregateByParam(context), aggregateByParam.returnType)

        val row = Row.withNames()
        row.setField(groupByInternalColumnName, evaluatedGroupBy)
        row.setField(aggregateByInternalColumnName, evaluatedAggregateBy)
        row
      }
    }

  }

  private object GroupByInputPreparingFunction {

    val outputTypeInfo: TypeInformation[Row] = Types.ROW_NAMED(
      Array(groupByInternalColumnName, aggregateByInternalColumnName),
      TypeInformationDetection.instance.forType(
        ToTableTypeEncoder.alignTypingResult(groupByLazyParam.returnType)
      ),
      TypeInformationDetection.instance.forType(
        ToTableTypeEncoder.alignTypingResult(aggregateByLazyParam.returnType)
      )
    )

  }

  private class AggregateResultContextFunction(convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext)
      extends ProcessFunction[Row, ValueWithContext[AnyRef]] {

    @transient
    private var contextIdGenerator: ContextIdGenerator = _

    override def open(openContext: OpenContext): Unit = {
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

  private object AggregateResultContextFunction {

    val outputTypeInfo: TypeInformation[ValueWithContext[AnyRef]] =
      TypeInformationDetection.instance.forValueWithContext[AnyRef](
        validationContext = ValidationContext.empty
          .withVariableUnsafe(KeyVariableName, ToTableTypeEncoder.alignTypingResult(groupByLazyParam.returnType)),
        value = ToTableTypeEncoder.alignTypingResult(aggregationResultType)
      )

  }

}
