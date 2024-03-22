package pl.touk.nussknacker.engine.flink.table.aggregate

import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.api.Expressions.{$, call}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.VariableConstants.KeyVariableName
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
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
  // TODO local: check this
  val outputVarParamName: ParameterName = ParameterName(OutputVar.CustomNodeFieldName)

  private val groupByParam: ParameterExtractor[LazyParameter[AnyRef]] with ParameterCreatorWithNoDependency =
    ParameterDeclaration.lazyMandatory[AnyRef](groupByParamName).withCreator()

  private val aggregateByParam: ParameterExtractor[LazyParameter[AnyRef]] with ParameterCreatorWithNoDependency =
    ParameterDeclaration.lazyMandatory[AnyRef](aggregateByParamName).withCreator()

  private val aggregatorFunctionParam = {
    val aggregators = Aggregator.allAggregators.map(a => FixedExpressionValue(s"'${a.name}'", a.name))
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
          (`groupByParamName`, groupByParam) ::
          (`aggregateByParamName`, aggregateByParam) ::
          (`aggregatorFunctionParamName`, DefinedEagerParameter(aggregatorName: String, _)) :: Nil,
          _
        ) =>
      val outName = OutputVariableNameDependency.extract(dependencies)

      val selectedAggregator = Aggregator.allAggregators
        .find(_.name == aggregatorName)
        .getOrElse(throw new IllegalStateException("Aggregator not found. Should be invalid at parameter level."))

      val aggregatorOutputType = selectedAggregator.outputType(aggregateByParam.returnType)

      val aggregateByTypeErrors = selectedAggregator.inputTypeConstraint match {
        case Some(typeConstraint) =>
          if (!aggregateByParam.returnType.canBeSubclassOf(typeConstraint)) {
            List(
              // TODO: this is a different message from other aggregators - choose one and make it consistent for all
              CustomNodeError(
                s"""Invalid type: "${aggregateByParam.returnType.withoutValue.display}" for selected aggregator.
                   |"${selectedAggregator.name}" aggregator requires type: "${typeConstraint.display}".
                   |""".stripMargin,
                Some(aggregateByParamName)
              )
            )
          } else List.empty
        case None => List.empty
      }

      FinalResults.forValidation(context, errors = aggregateByTypeErrors)(
        _.withVariable(outName, value = aggregatorOutputType, paramName = Some(outputVarParamName)).andThen(
          _.withVariable(
            KeyVariableName,
            value = groupByParam.returnType,
            paramName = Some(ParameterName(KeyVariableName))
          )
        )
      )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomStreamTransformation = {

    val groupByLazyParam     = groupByParam.extractValueUnsafe(params)
    val aggregateByLazyParam = aggregateByParam.extractValueUnsafe(params)
    val aggregatorVal        = aggregatorFunctionParam.extractValueUnsafe(params)

    val aggregator = Aggregator.allAggregators
      .find(_.name == aggregatorVal)
      .getOrElse(
        throw new IllegalStateException("Specified aggregator not found. Should be invalid at parameter level.")
      )

    // TODO local: calculate based on groupByLazyParam.returnType
    val groupByFlinkType     = DataTypes.STRING()
    val aggregateByFlinkType = DataTypes.INT()

    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      val env = start.getExecutionEnvironment
      // Setting batch mode to enable global window operations. If source is unbounded it will throw a runtime exception
      env.setRuntimeMode(RuntimeExecutionMode.BATCH)

      val tableEnv = StreamTableEnvironment.create(env)

      val streamOfRows = start.flatMap(new LazyInterpreterFunction(groupByLazyParam, aggregateByLazyParam, ctx))

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
          call(aggregator.flinkFunctionName, $(aggregateByInternalColumnName)).as(aggregateByInternalColumnName)
        )

      // TODO local: pass aggregated value and key as separate vars
      val groupedStream = tableEnv.toDataStream(groupedTable)

      // TODO local: use process function to build a new context with EngineRuntimeContext.contextIdGenerator
      groupedStream
        .map(r => {
          val map = RowConversions.rowToMap(r)
          ValueWithContext(
            map.get(aggregateByInternalColumnName).asInstanceOf[AnyRef],
            Context.withInitialId.withVariable(KeyVariableName, map.get(groupByInternalColumnName))
          )
        })
        .returns(classOf[ValueWithContext[AnyRef]])
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

object Aggregator {
  val allAggregators: List[Aggregator] = List(Sum, First)
}

sealed trait Aggregator {
  val name: String
  val inputTypeConstraint: Option[TypingResult]
  def outputType(inputTypeInRuntime: TypingResult): TypingResult
  val flinkFunctionName: String
}

case object Sum extends Aggregator {
  override val name: String = "Sum"
  // TODO local: is Typed[Number] ok?
  override val inputTypeConstraint: Option[TypingResult]                  = Some(Typed[Number])
  override def outputType(inputTypeInRuntime: TypingResult): TypingResult = Typed[Number]
  override val flinkFunctionName: String                                  = "sum"
}

case object First extends Aggregator {
  override val name: String                                               = "First"
  override val inputTypeConstraint: Option[TypingResult]                  = None
  override def outputType(inputTypeInRuntime: TypingResult): TypingResult = inputTypeInRuntime
  override val flinkFunctionName: String                                  = "first_value"
}
