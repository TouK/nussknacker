package pl.touk.nussknacker.engine.common.components

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.TabularDataDefinitionParserErrorDetails
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
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

  private object MatchConditionTableExpressionParameter {
    val name: ParameterName = ParameterName("Match condition")

    val declaration
        : ParameterCreator[Iterable[Column.Definition]] with ParameterExtractor[LazyParameter[lang.Boolean]] = {
      ParameterDeclaration
        .lazyMandatory[java.lang.Boolean](name)
        .withAdvancedCreator[Iterable[Column.Definition]](
          create = columnDefinitions =>
            _.copy(additionalVariables =
              Map(
                decisionTableRowRuntimeVariableName -> AdditionalVariableProvidedInRuntime(
                  rowDataTypingResult(columnDefinitions)
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
    prepare orElse basicDecisionTableParameterDefined orElse allParametersDefined(context, dependencies)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Unit]
  ): ServiceInvoker = {
    val tabularTypedData = BasicDecisionTableParameter.declaration.extractValueUnsafe(params)
    val matchCondition   = MatchConditionTableExpressionParameter.declaration.extractValueUnsafe(params)
    new DecisionTableImplementation(tabularTypedData, matchCondition)
  }

  private lazy val prepare: ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(
      parameters = BasicDecisionTableParameter.declaration.createParameter() :: Nil,
      errors = List.empty,
      state = None
    )
  }

  private lazy val basicDecisionTableParameterDefined: ContextTransformationDefinition = {
    case TransformationStep((name, DefinedEagerParameter(data: TabularTypedData, _)) :: Nil, _)
        if name == BasicDecisionTableParameter.name =>
      NextParameters(
        parameters = MatchConditionTableExpressionParameter.declaration.createParameter(data.columnDefinitions) :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep((name, FailedToDefineParameter(errors)) :: Nil, _)
        if name == BasicDecisionTableParameter.name =>
      val columnDefinitions = extractColumnDefinitionsFrom(errors).getOrElse(TabularTypedData.empty.columnDefinitions)
      NextParameters(
        parameters = MatchConditionTableExpressionParameter.declaration.createParameter(columnDefinitions) :: Nil,
        errors = List.empty,
        state = None
      )
  }

  private def allParametersDefined(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (firstParamName, FailedToDefineParameter(_)) ::
          (secondParamName, _) :: Nil,
          _
        )
        if firstParamName == BasicDecisionTableParameter.name &&
          secondParamName == MatchConditionTableExpressionParameter.name =>
      FinalResults.forValidation(context)(
        _.withVariable(
          name = OutputVariableNameDependency.extract(dependencies),
          value = Unknown,
          paramName = None
        )
      )
    case TransformationStep(
          (firstParamName, DefinedEagerParameter(data: TabularTypedData, _)) ::
          (secondParamName, _) :: Nil,
          _
        )
        if firstParamName == BasicDecisionTableParameter.name &&
          secondParamName == MatchConditionTableExpressionParameter.name =>
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
    Typed.record(
      columnDefinitions.map { columnDef =>
        columnDef.name -> Typed.typedClass(columnDef.aType)
      }.toMap
    )

  private def extractColumnDefinitionsFrom(errors: NonEmptyList[ProcessCompilationError]) = {
    errors.toList
      .flatMap {
        case e: ProcessCompilationError.ExpressionParserCompilationError =>
          e.details match {
            case Some(details: TabularDataDefinitionParserErrorDetails) => Some(details)
            case Some(_) | None                                         => None
          }
        case _ =>
          None
      }
      .map(_.columnDefinitions.map(cd => Column.Definition(cd.name, cd.aType)))
      .headOption
  }

}
