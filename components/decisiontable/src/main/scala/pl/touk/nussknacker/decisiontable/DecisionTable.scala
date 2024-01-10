package pl.touk.nussknacker.decisiontable

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputGenericNodeTransformation
}
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData.Row
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.lazyparam.EvaluableLazyParameter
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}

import scala.concurrent.{ExecutionContext, Future}

object DecisionTable extends Service {

  @MethodToInvoke
  def invoke(
      @ParamName("Basic Decision Table")
      @SimpleEditor(`type` = SimpleEditorType.TYPED_TABULAR_DATA_EDITOR) tabularData: TabularTypedData,
      @ParamName("Expression") expression: java.lang.Boolean,
      @OutputVariableName outputVariable: String
  )(implicit nodeId: NodeId): Future[Vector[Row]] = Future.successful {
    tabularData.rows
  }

}

object DecisionTable2 extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {

  override type State = DecisionTableComponentState

  override val nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): NodeTransformationDefinition = {
    case TransformationStep(_, None) =>
      NextParameters(
        parameters = decisionTableParameter :: Nil,
        errors = List.empty,
        state = Some(DecisionTableComponentState.Initiated)
      )
    case TransformationStep(
          (`decisionTableParameterName`, DefinedEagerParameter(data: TabularTypedData, _)) :: Nil,
          Some(DecisionTableComponentState.Initiated)
        ) =>
      NextParameters(
        parameters = filterDecisionTableExpressionParameter(data) :: Nil,
        errors = List.empty,
        state = Some(DecisionTableComponentState.Configured)
      )
    case TransformationStep(
          (`decisionTableParameterName`, DefinedEagerParameter(data: TabularTypedData, _)) ::
          (`filterDecisionTableExpressionParameterName`, _) :: Nil,
          Some(DecisionTableComponentState.Configured)
        ) =>
      FinalResults.forValidation(context, errors = List.empty)(
        _.withVariable(
          name = OutputVariableNameDependency.extract(dependencies),
          value = Typed.fromInstance(data.rows), // todo: do it better
          paramName = None
        )
      )
  }

  override def implementation(
      params: Map[String, Any],
      dependencies: List[NodeDependencyValue],
      finalState: Option[DecisionTableComponentState]
  ): ServiceInvoker =
    new DecisionTableComponentLogic(
      params(decisionTableParameterName).asInstanceOf[TabularTypedData],
      params(filterDecisionTableExpressionParameterName).asInstanceOf[EvaluableLazyParameter[java.lang.Boolean]]
    )

  private lazy val decisionTableParameterName = "Basic Decision Table"

  private lazy val decisionTableParameter =
    Parameter(
      decisionTableParameterName,
      Typed[TabularTypedData]
    ).copy(
      editor = Some(TabularTypedDataEditor)
    )

  private lazy val filterDecisionTableExpressionParameterName = "Expression"

  private def filterDecisionTableExpressionParameter(data: TabularTypedData) =
    Parameter(
      filterDecisionTableExpressionParameterName,
      Typed[java.lang.Boolean]
    ).copy(
      isLazyParameter = true,
      additionalVariables = Map(
        "DecisionTable" -> AdditionalVariableProvidedInRuntime(
          TypedObjectTypingResult(
            data.columns
              .map(_.definition)
              .map { columnDef =>
                columnDef.name -> Typed.typedClass(columnDef.aType)
              }
              .toMap
          )
        )
      )
    )

  sealed trait DecisionTableComponentState

  object DecisionTableComponentState {
    case object Initiated  extends DecisionTableComponentState
    case object Configured extends DecisionTableComponentState
  }

  private class DecisionTableComponentLogic(
      tabularData: TabularTypedData,
      filterTabularDataExpression: EvaluableLazyParameter[java.lang.Boolean]
  ) extends ServiceInvoker {

    override def invokeService(params: Map[String, Any])(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        contextId: ContextId,
        componentUseCase: ComponentUseCase
    ): Future[Any] = Future.successful {
      filterTabularDataExpression.prepareEvaluator(???)
      ???
    }

  }

}
