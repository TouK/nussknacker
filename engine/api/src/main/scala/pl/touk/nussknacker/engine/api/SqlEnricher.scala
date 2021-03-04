package pl.touk.nussknacker.engine.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, FailedToDefineParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, NodeDependency, OutputVariableNameDependency, Parameter, TextareaParameterEditor, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import scala.concurrent.{ExecutionContext, Future}

object SqlEnricher {

  final val ArgPrefix: String = "arg"

  final val TableNameParamName: String = "Table"

  final val WhereClauseParamName: String = "Where"

  private val metaData = TypedNodeDependency(classOf[MetaData])

  private val WhereClauseParam: Parameter = Parameter(WhereClauseParamName, Typed[String]).copy(
    editor = Some(TextareaParameterEditor))

  private def tableNameParam(tableNames: List[String]): Parameter =
    Parameter[String](TableNameParamName).copy(
      editor = Some(FixedValuesParameterEditor(tableNames.map(name => FixedExpressionValue(s"'$name'", name))))
    )


  case class TransformationState(outputType: TypingResult, queryArgs: List[Parameter])
}

class SqlEnricher extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {
  import SqlEnricher._

  override type State = TransformationState

  override val nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: metaData :: Nil

  override def initialParameters: List[Parameter] = tableNameParam(tableNames()) :: WhereClauseParam :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(initialParameters)

    case TransformationStep((TableNameParamName, DefinedEagerParameter(tableName: String, _)) :: (WhereClauseParamName, DefinedEagerParameter(whereClause: String, _)) :: Nil, None) =>
      val queryArgParams = (1 to whereClause.count(_ == '?')).map { n =>
        Parameter(name = s"$ArgPrefix$n", typ = typing.Unknown).copy(isLazyParameter = true)
      }.toList
      NextParameters(
        parameters = queryArgParams,
        state = Some(TransformationState(
          outputType = tableType(tableName),
          queryArgs = queryArgParams
        )))

    case TransformationStep(_, state@Some(TransformationState(outputType, _))) =>
      val newCtxV = context.withVariable(
        name = OutputVariableNameDependency.extract(dependencies),
        value = outputType,
        paramName = None)
      FinalResults(
        finalContext = newCtxV.getOrElse(context),
        errors = newCtxV.swap.map(_.toList).getOrElse(Nil),
        state = state)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[TransformationState]): ServiceInvoker = {
    val tableName = params(TableNameParamName).asInstanceOf[String]
    val whereClause = params(WhereClauseParamName).asInstanceOf[String]
    val query = s"select * from $tableName where $whereClause;"
    new ServiceInvoker {
      override def invokeService(params: Map[String, Any])
                                (implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, contextId: ContextId): Future[Any] = {
        val queryArgs = finalState
          .map(_.queryArgs.map { param => params(param.name).asInstanceOf[AnyRef] })
          .getOrElse(Nil)

        Future.successful {
        }
      }

      override def returnType: TypingResult = finalState.map(_.outputType).getOrElse(typing.Unknown)
    }
  }

  private def tableNames(): List[String] = "ACCOUNTS" :: Nil

  private def tableType(tableName: String): TypingResult = typing.Unknown
}
