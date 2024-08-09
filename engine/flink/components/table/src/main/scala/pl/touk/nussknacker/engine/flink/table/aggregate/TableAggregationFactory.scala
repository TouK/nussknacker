package pl.touk.nussknacker.engine.flink.table.aggregate

import pl.touk.nussknacker.engine.api.VariableConstants.KeyVariableName
import pl.touk.nussknacker.engine.api._
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
      TableAggregator.values.map(a => FixedExpressionValue(s"'${a.displayName}'", a.displayName)).toList
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

  case class TableAggregationTransformationState(aggregatorOutputType: TypingResult)
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

      val selectedAggregator = TableAggregator.values
        .find(_.displayName == aggregatorName)
        .getOrElse(throw new IllegalStateException("Aggregator not found. Should be invalid at parameter level."))

      val (aggregatorTypeErrors, aggregatorOutputType) = selectedAggregator
        .inferOutputType(aggregateByParam.returnType)
        .fold(
          errors => (errors :: Nil, Unknown),
          outputType => (Nil, outputType)
        )

      FinalResults.forValidation(
        context,
        errors = aggregatorTypeErrors,
        state = Some(TableAggregationTransformationState(aggregatorOutputType))
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
    val aggregatorVal        = aggregatorFunctionParam.extractValueUnsafe(params)
    val aggregationResultType = finalState
      .getOrElse(
        throw new IllegalStateException(
          "Context transformation state was not properly passed to component's implementation."
        )
      )
      .aggregatorOutputType

    val aggregator = TableAggregator.values
      .find(_.displayName == aggregatorVal)
      .getOrElse(
        throw new IllegalStateException("Specified aggregator not found. Should be invalid at parameter level.")
      )

    val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

    new TableAggregation(
      groupByLazyParam = groupByLazyParam,
      aggregateByLazyParam = aggregateByLazyParam,
      selectedAggregator = aggregator,
      aggregationResultType = aggregationResultType,
      nodeId = nodeId
    )
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency[NodeId])

}
