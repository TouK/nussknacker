package pl.touk.nussknacker.engine.flink.table.aggregate

import org.apache.flink.table.api.DataTypes.RAW
import org.apache.flink.table.types.logical.LogicalTypeRoot
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
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory._
import pl.touk.nussknacker.engine.flink.table.utils.ToTableTypeEncoder
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions.TypingResultExtension

object TableAggregationFactory {

  val groupByParamName: ParameterName            = ParameterName("groupBy")
  val aggregateByParamName: ParameterName        = ParameterName("aggregateBy")
  val aggregatorFunctionParamName: ParameterName = ParameterName("aggregator")
  private val outputVarParamName: ParameterName  = ParameterName(OutputVar.CustomNodeFieldName)

  private val groupByParam: ParameterExtractor[LazyParameter[AnyRef]] with ParameterCreatorWithNoDependency =
    ParameterDeclaration.lazyMandatory[AnyRef](groupByParamName).withCreator()

  private val aggregateByParam: ParameterExtractor[LazyParameter[AnyRef]] with ParameterCreatorWithNoDependency =
    ParameterDeclaration.lazyMandatory[AnyRef](aggregateByParamName).withCreator()

  private val aggregatorFunctionParam = {
    val aggregators =
      TableAggregatorType.values.map(a => FixedExpressionValue(s"'${a.displayName}'", a.displayName)).toList
    ParameterDeclaration
      .mandatory[String](aggregatorFunctionParamName)
      .withCreator(
        modify = _.copy(editor = Some(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: aggregators)))
      )
  }

}

class TableAggregationFactory
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[FlinkCustomStreamTransformation] {

  case class TableAggregationTransformationState(
      selectedAggregator: TableAggregatorType,
      aggregatorResultType: TypingResult
  )

  override type State = TableAggregationTransformationState

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

      // RAW's in groupBy cause a InvalidProgramException with mesage "Table program cannot be compiled"
      val groupByError =
        if (ToTableTypeEncoder
            .alignTypingResult(groupByParam.returnType)
            .toDataType
            .getLogicalType
            .is(LogicalTypeRoot.RAW)) {
          Some(CustomNodeError(s"Invalid type: ${groupByParam.returnType}", Some(groupByParamName)))
        } else {
          None
        }

      val selectedAggregator = TableAggregatorType.values
        .find(_.displayName == aggregatorName)
        .getOrElse(throw new IllegalStateException("Aggregator not found. Should be invalid at parameter level."))

      val (aggregatorTypeErrors, aggregatorOutputType) = selectedAggregator
        .inferOutputType(ToTableTypeEncoder.alignTypingResult(aggregateByParam.returnType))
        .fold(
          errors => (errors :: Nil, Unknown),
          outputType => (Nil, outputType)
        )

      FinalResults.forValidation(
        context,
        errors = aggregatorTypeErrors ++ groupByError.toList,
        state = Some(TableAggregationTransformationState(selectedAggregator, aggregatorOutputType))
      )(ctx =>
        ctx.clearVariables
          .withVariable(outName, value = aggregatorOutputType, paramName = Some(outputVarParamName))
          .andThen(
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
    val TableAggregationTransformationState(selectedAggregator, aggregatorOutputType) = finalState
      .getOrElse(
        throw new IllegalStateException(
          "Context transformation state was not properly passed to component's implementation."
        )
      )

    val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

    new TableAggregation(
      groupByLazyParam = groupByLazyParam,
      aggregateByLazyParam = aggregateByLazyParam,
      selectedAggregator = selectedAggregator,
      aggregationResultType = aggregatorOutputType,
      nodeId = nodeId
    )
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency[NodeId])

}
