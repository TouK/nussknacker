package pl.touk.nussknacker.engine.common.components

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputGenericNodeTransformation
}
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData.Column
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DecisionTable extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {

  override type State = Unit

  override val nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = decisionTableParameter :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (`decisionTableParameterName`, DefinedEagerParameter(data: TabularTypedData, _)) :: Nil,
          _
        ) =>
      NextParameters(
        parameters = filterDecisionTableExpressionParameter(data) :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (`decisionTableParameterName`, DefinedEagerParameter(data: TabularTypedData, _)) ::
          (`filterDecisionTableExpressionParameterName`, _) :: Nil,
          _
        ) =>
      FinalResults.forValidation(context)(
        _.withVariable(
          name = OutputVariableNameDependency.extract(dependencies),
          value = componentResultTypingResult(data.columnDefinitions),
          paramName = None
        )
      )
  }

  override def implementation(
      params: Map[String, Any],
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): ServiceInvoker =
    new DecisionTableComponentLogic(
      params(decisionTableParameterName).asInstanceOf[TabularTypedData]
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
        "ROW" -> AdditionalVariableProvidedInRuntime(rowDataTypingResult(data.columnDefinitions))
      )
    )

  private def componentResultTypingResult(columnDefinitions: Iterable[Column.Definition]): TypingResult = {
    Typed.genericTypeClass(classOf[List[Map[String, Any]]], rowDataTypingResult(columnDefinitions) :: Nil)
  }

  private def rowDataTypingResult(columnDefinitions: Iterable[Column.Definition]) =
    TypedObjectTypingResult(
      columnDefinitions.map { columnDef =>
        columnDef.name -> Typed.typedClass(columnDef.aType)
      }.toMap
    )

  private class DecisionTableComponentLogic(
      tabularData: TabularTypedData,
  ) extends ServiceInvoker {

    override def invokeService(evaluateParams: Context => (Context, Map[String, Any]))(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        context: Context,
        componentUseCase: ComponentUseCase
    ): Future[Any] = Future.successful {
      filterRows(tabularData, evaluateParams, context)
    }

    private def filterRows(
        tabularData: TabularTypedData,
        evaluateParams: Context => (Context, Map[String, Any]),
        context: Context
    ): java.util.List[java.util.Map[String, Any]] = {
      tabularData.rows
        .filter { row =>
          val m           = row.cells.map(c => (c.definition.name, c.value)).toMap.asJava
          val newContext  = context.withVariables(Map("ROW" -> m))
          val (_, params) = evaluateParams(newContext)
          params("Expression").asInstanceOf[java.lang.Boolean]
        }
        .map { row =>
          row.cells.map(c => c.definition.name -> c.value).toMap // todo: are we sure keys are unique
        }
        .map(_.asJava)
        .asJava
    }

  }

}
