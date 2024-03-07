package pl.touk.nussknacker.engine.common.components

import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.TabularTypedData
import pl.touk.nussknacker.engine.api.definition.TabularTypedData.Column
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DecisionTable extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

  override type State = Unit

  private type Output = java.util.List[java.util.Map[String, Any]]

  private object BasicDecisionTableParameter {
    val name = "Basic Decision Table"

    val declaration: ParameterWithExtractor[TabularTypedData] =
      ParameterWithExtractor
        .mandatory[TabularTypedData](
          name = name,
          modify = _.copy(editor = Some(TabularTypedDataEditor))
        )

  }

  private object FilterDecisionTableExpressionParameter {
    val name = "Expression"

    def declaration(data: TabularTypedData): ParameterWithExtractor[api.LazyParameter[java.lang.Boolean]] = {
      ParameterWithExtractor
        .lazyMandatory[java.lang.Boolean](
          name,
          _.copy(additionalVariables =
            Map(
              decisionTableRowRuntimeVariableName -> AdditionalVariableProvidedInRuntime(
                rowDataTypingResult(data.columnDefinitions)
              )
            )
          )
        )
    }

  }

  private val decisionTableRowRuntimeVariableName = "ROW"

  override val nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    prepare orElse basicDecisionTableParameterReady orElse allParametersReady(context, dependencies)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): ServiceInvoker = {
    val tabularTypedData = BasicDecisionTableParameter.declaration.extractValue(params)
    val filterExpression = FilterDecisionTableExpressionParameter.declaration(tabularTypedData).extractValue(params)
    new DecisionTableImplementation(tabularTypedData, filterExpression)
  }

  private lazy val prepare: ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(
      parameters = BasicDecisionTableParameter.declaration.parameter :: Nil,
      errors = List.empty,
      state = None
    )
  }

  private lazy val basicDecisionTableParameterReady: ContextTransformationDefinition = {
    case TransformationStep((name, DefinedEagerParameter(data: TabularTypedData, _)) :: Nil, _)
        if name == BasicDecisionTableParameter.name =>
      NextParameters(
        parameters = FilterDecisionTableExpressionParameter.declaration(data).parameter :: Nil,
        errors = List.empty,
        state = None
      )
  }

  private class DecisionTableImplementation(
      tabularData: TabularTypedData,
      expression: LazyParameter[java.lang.Boolean]
  ) extends ServiceInvoker {

    override def invoke(context: Context)(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        componentUseCase: ComponentUseCase
    ): Future[Output] = Future {
      filterRows(tabularData, context)
    }

    private def filterRows(
        tabularData: TabularTypedData,
        context: Context
    ): Output = {
      tabularData.rows
        .filter { row =>
          val m            = row.cells.map(c => (c.definition.name, c.value)).toMap.asJava
          val localContext = context.withVariables(Map(decisionTableRowRuntimeVariableName -> m))
          val result       = expression.evaluate(localContext)
          result
        }
        .map { row =>
          row.cells.map(c => c.definition.name -> c.value).toMap
        }
        .map(_.asJava)
        .asJava
    }

  }

  private def allParametersReady(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (firstParamName, DefinedEagerParameter(data: TabularTypedData, _)) ::
          (secondParamName, _) :: Nil,
          _
        )
        if firstParamName == BasicDecisionTableParameter.name &&
          secondParamName == FilterDecisionTableExpressionParameter.name =>
      FinalResults.forValidation(context)(
        _.withVariable(
          name = OutputVariableNameDependency.extract(dependencies),
          value = componentResultTypingResult(data.columnDefinitions),
          paramName = None
        )
      )
  }

  private def componentResultTypingResult(columnDefinitions: Iterable[Column.Definition]): TypingResult = {
    Typed.genericTypeClass(classOf[Output], rowDataTypingResult(columnDefinitions) :: Nil)
  }

  private def rowDataTypingResult(columnDefinitions: Iterable[Column.Definition]) =
    TypedObjectTypingResult(
      columnDefinitions.map { columnDef =>
        columnDef.name -> Typed.typedClass(columnDef.aType)
      }.toMap
    )

}
