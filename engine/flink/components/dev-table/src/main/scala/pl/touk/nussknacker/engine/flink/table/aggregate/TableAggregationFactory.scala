package pl.touk.nussknacker.engine.flink.table.aggregate

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
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
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
    val aggregators = TableAggregator.values.map(a => FixedExpressionValue(s"'${a.name}'", a.name)).toList
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

      val selectedAggregator = TableAggregator.values
        .find(_.name == aggregatorName)
        .getOrElse(throw new IllegalStateException("Aggregator not found. Should be invalid at parameter level."))

      val aggregatorOutputType = selectedAggregator.outputType(aggregateByParam.returnType)

      val aggregateByTypeErrors = selectedAggregator.inputTypeConstraint match {
        case Some(typeConstraint) =>
          if (!aggregateByParam.returnType.canBeSubclassOf(typeConstraint)) {
            List(
              // TODO: this is a different message from other aggregators - choose one and make it consistent for all
              CustomNodeError(
                aggregateByTypeMismatchErrorMessage(
                  aggregateByParam.returnType,
                  typeConstraint,
                  selectedAggregator.name
                ),
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

  private def aggregateByTypeMismatchErrorMessage(
      aggregateByType: TypingResult,
      aggregatorFunctionTypeConstraint: TypingResult,
      aggregatorName: String,
  ): String =
    s"""Invalid type: ${aggregateByType.withoutValue.display}" for selected aggregator.
      |"$aggregatorName" aggregator requires type: "${aggregatorFunctionTypeConstraint.display}".""".stripMargin

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): FlinkCustomStreamTransformation = {

    val groupByLazyParam     = groupByParam.extractValueUnsafe(params)
    val aggregateByLazyParam = aggregateByParam.extractValueUnsafe(params)
    val aggregatorVal        = aggregatorFunctionParam.extractValueUnsafe(params)

    val aggregator = TableAggregator.values
      .find(_.name == aggregatorVal)
      .getOrElse(
        throw new IllegalStateException("Specified aggregator not found. Should be invalid at parameter level.")
      )

    val nodeId: NodeId = TypedNodeDependency[NodeId].extract(dependencies)

    new TableAggregation(
      groupByLazyParam = groupByLazyParam,
      aggregateByLazyParam = aggregateByLazyParam,
      selectedAggregator = aggregator,
      nodeId = nodeId
    )
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency[NodeId])

}
