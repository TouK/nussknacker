package pl.touk.nussknacker.engine.common.components

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData.Column
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object DecisionTable extends EagerService with SingleInputDynamicComponent[ServiceLogic] {

  override type State = Unit

  override val nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
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

  override def createComponentLogic(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): ServiceLogic =
    new DecisionTableComponentLogic(
      params.extractUnsafe(decisionTableParameterName),
      params.extractUnsafe(filterDecisionTableExpressionParameterName)
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
      expression: LazyParameter[java.lang.Boolean]
  ) extends ServiceLogic {

    override def run(context: Context)(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        componentUseCase: ComponentUseCase
    ): Future[Any] = Future {
      filterRows(tabularData, context)
    }

    private def filterRows(
        tabularData: TabularTypedData,
        context: Context
    ): java.util.List[java.util.Map[String, Any]] = {
      tabularData.rows
        .filter { row =>
          val m          = row.cells.map(c => (c.definition.name, c.value)).toMap.asJava
          val newContext = context.withVariables(Map("ROW" -> m))
          val result     = expression.evaluate(newContext)
          result
        }
        .map { row =>
          row.cells.map(c => c.definition.name -> c.value).toMap // todo: are we sure keys are unique
        }
        .map(_.asJava)
        .asJava
    }

  }

}
