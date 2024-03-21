package pl.touk.nussknacker.engine.flink.table.aggregate

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.functions.{AggregateFunction, FlatMapFunction}
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.table.api.Expressions.{$, call}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.internal.BaseExpressions
import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.functions.{BuiltInFunctionDefinition, BuiltInFunctionDefinitions}
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkCustomNodeContext,
  FlinkCustomStreamTransformation
}
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregation._
import pl.touk.nussknacker.engine.flink.table.utils.NestedRowConversions.ColumnFlinkSchema
import pl.touk.nussknacker.engine.flink.table.utils.{NestedRowConversions, RowConversions}

object TableAggregation {

  val groupByParamName: ParameterName            = ParameterName("groupBy")
  val aggregateByParamName: ParameterName        = ParameterName("aggregateBy")
  val aggregatorFunctionParamName: ParameterName = ParameterName("aggregator")
  val outputVarParamName: ParameterName          = ParameterName(OutputVar.CustomNodeFieldName)

  private val groupByParam: ParameterExtractor[LazyParameter[AnyRef]] with ParameterCreatorWithNoDependency =
    ParameterDeclaration.lazyMandatory[AnyRef](groupByParamName).withCreator()

  private val aggregateByParam: ParameterExtractor[LazyParameter[AnyRef]] with ParameterCreatorWithNoDependency =
    ParameterDeclaration.lazyMandatory[AnyRef](aggregateByParamName).withCreator()

  private val aggregatorFunctionParam = {
    val aggregators = List(
      FixedExpressionValue(s"'Sum'", "Sum")
    )
    ParameterDeclaration
      .mandatory[String](aggregatorFunctionParamName)
      .withCreator(
        modify = _.copy(editor = Some(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: aggregators)))
      )
  }

  private val aggregateByInternalColumnName = "aggregateByInternalColumn"
  private val groupByInternalColumnName     = "groupByInternalColumn"

}

class TableAggregation
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation] {

  override type State = Nothing

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = groupByParam
          .createParameter() :: aggregateByParam.createParameter() :: aggregatorFunctionParam.createParameter() :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (`groupByParamName`, _) ::
          (`aggregateByParamName`, _) ::
          (`aggregatorFunctionParamName`, _) :: Nil,
          _
        ) =>
      val outName = OutputVariableNameDependency.extract(dependencies)
      FinalResults.forValidation(context, errors = Nil)(
        _.withVariable(outName, value = typing.Unknown, paramName = Some(outputVarParamName))
      )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomStreamTransformation = {

    val groupByLazyParam     = groupByParam.extractValueUnsafe(params)
    val aggregateByLazyParam = aggregateByParam.extractValueUnsafe(params)

    // TODO: make aggregator function configurable
    val aggregatorVal = aggregatorFunctionParam.extractValueUnsafe(params)

    val outName = OutputVariableNameDependency.extract(dependencies)

    val groupByFlinkType     = DataTypes.STRING()
    val aggregateByFlinkType = DataTypes.INT()

    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      val env = start.getExecutionEnvironment
      // Setting batch mode to enable global window operations. If source is unbounded it will throw a runtime exception
      env.setRuntimeMode(RuntimeExecutionMode.BATCH)

      val tableEnv = StreamTableEnvironment.create(env)

      val streamOfRows = start.flatMap(new LazyInterpreterFunction(groupByLazyParam, aggregateByLazyParam, ctx))

      val inputTable = NestedRowConversions.buildTableFromRowStream(
        tableEnv = tableEnv,
        streamOfRows = streamOfRows,
        columnSchema = List(
          ColumnFlinkSchema(groupByInternalColumnName, groupByFlinkType),
          ColumnFlinkSchema(aggregateByInternalColumnName, aggregateByFlinkType)
        )
      )

      val groupedTable = inputTable
        .groupBy($(groupByInternalColumnName))
        .select(
          $(groupByInternalColumnName),
          $(aggregateByInternalColumnName).sum().as("aggregatedSum")
        )

      // TODO local: pass aggregated value and key as separate vars
      val groupedStream = tableEnv.toDataStream(groupedTable)

      val groupedStreamOfMaps = groupedStream
        .map(r => RowConversions.rowToMap(r): java.util.Map[String, Any])
        .returns(classOf[java.util.Map[String, Any]])

      val mergedStream: DataStream[ValueWithContext[AnyRef]] = groupedStreamOfMaps
        .map(row => ValueWithContext(row.asInstanceOf[AnyRef], Context.withInitialId))
        .returns(classOf[ValueWithContext[AnyRef]])

      mergedStream
    })
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  private class LazyInterpreterFunction(
      groupByParam: LazyParameter[AnyRef],
      aggregateByParam: LazyParameter[AnyRef],
      customNodeContext: FlinkCustomNodeContext
  ) extends AbstractLazyParameterInterpreterFunction(customNodeContext.lazyParameterHelper)
      with FlatMapFunction[Context, Row] {

    private lazy val evaluateGroupBy          = toEvaluateFunctionConverter.toEvaluateFunction(groupByParam)
    private lazy val evaluateAggregateByParam = toEvaluateFunctionConverter.toEvaluateFunction(aggregateByParam)

    /*
     Has to out Rows?
     Otherwise org.apache.flink.util.FlinkRuntimeException: Error during input conversion from external DataStream API to
     internal Table API data structures. Make sure that the provided data types that configure the converters are
     correctly declared in the schema.
     */
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
