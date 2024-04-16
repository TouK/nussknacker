package pl.touk.nussknacker.engine.common.components

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  FailedToDefineParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Column

import java.lang
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DecisionTable extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

  override type State = Unit

  private type Output = java.util.List[java.util.Map[String, Any]]

  private object BasicDecisionTableParameter {
    val name: ParameterName = ParameterName("Decision Table")

    val declaration: ParameterExtractor[TabularTypedData] with ParameterCreatorWithNoDependency =
      ParameterDeclaration
        .mandatory[TabularTypedData](name)
        .withCreator(_.copy(editor = Some(TabularTypedDataEditor)))

  }

  private object FilterDecisionTableExpressionParameter {
    val name: ParameterName = ParameterName("Filtering expression")

    val declaration: ParameterCreator[TabularTypedData] with ParameterExtractor[LazyParameter[lang.Boolean]] = {
      ParameterDeclaration
        .lazyMandatory[java.lang.Boolean](name)
        .withAdvancedCreator[TabularTypedData](
          create = data =>
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
    prepare orElse basicDecisionTableParameterReady(context) orElse allParametersReady(
      context,
      dependencies
    ) orElse catchall
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): ServiceInvoker = {
    val tabularTypedData    = BasicDecisionTableParameter.declaration.extractValueUnsafe(params)
    val filteringExpression = FilterDecisionTableExpressionParameter.declaration.extractValueUnsafe(params)
    new DecisionTableImplementation(tabularTypedData, filteringExpression)
  }

  private lazy val prepare: ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(
      parameters = BasicDecisionTableParameter.declaration.createParameter() :: Nil,
      errors = List.empty,
      state = None
    )
  }

  private lazy val catchall: ContextTransformationDefinition = { case TransformationStep(aaa, ddd) =>
    throw new IllegalStateException(s"ddd + $aaa + $ddd")
  }

  private def basicDecisionTableParameterReady(
      context: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep((name, DefinedEagerParameter(data: TabularTypedData, _)) :: Nil, _)
        if name == BasicDecisionTableParameter.name =>
      NextParameters(
        parameters = FilterDecisionTableExpressionParameter.declaration.createParameter(data) :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (name, FailedToDefineParameter) ::
          (secondParamName, _) :: Nil,
          _
        )
        if name == BasicDecisionTableParameter.name &&
          secondParamName == FilterDecisionTableExpressionParameter.name =>
      FinalResults(
        finalContext = context,
        errors = Nil,
        state = None
      )
    case TransformationStep((name, FailedToDefineParameter) :: Nil, _) if name == BasicDecisionTableParameter.name =>
      NextParameters(
        parameters = FilterDecisionTableExpressionParameter.declaration.createParameter(TabularTypedData.empty) :: Nil,
        errors = List.empty,
        state = None
      )
//      FinalResults(
//        finalContext = context,
//        errors = CustomParameterValidationError("ERROR!!!", "error desc", name, nodeId.id) :: Nil,
//        state = None
//      )
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
          val rowData      = row.cells.map(c => (c.definition.name, c.value)).toMap.asJava
          val localContext = context.withVariables(Map(decisionTableRowRuntimeVariableName -> rowData))
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
