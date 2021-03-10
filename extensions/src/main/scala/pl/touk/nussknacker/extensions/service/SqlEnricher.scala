package pl.touk.nussknacker.extensions.service

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, typing}
import pl.touk.nussknacker.extensions.db.WithDBConnectionPool
import pl.touk.nussknacker.extensions.db.schema.TableDefinition

import java.sql.ParameterMetaData
import javax.sql.DataSource
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters.bufferAsJavaListConverter


object SqlEnricher {

  final val ArgPrefix: String = "arg"

  final val QueryParamName: String = "Query"

  private val metaData = TypedNodeDependency(classOf[MetaData])

  private val QueryParam: Parameter = Parameter(QueryParamName, Typed[String]).copy(
    editor = Some(SqlParameterEditor))

  case class TransformationState(query: String, argsCount: Int, tableDef: TableDefinition)
}

/*
TODO:
1. Named parameters. Maybe we can make use of Spring's NamedJdbcParameterTemplate?
2. Typed parameters - currently we type them as Objects/Unknowns
*/
class SqlEnricher(val dataSource: DataSource) extends EagerService
  with SingleInputGenericNodeTransformation[ServiceInvoker] with WithDBConnectionPool {
  import SqlEnricher._

  override type State = TransformationState

  override val nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: metaData :: Nil

  override def initialParameters: List[Parameter] = QueryParam :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(initialParameters)

    case TransformationStep((QueryParamName, DefinedEagerParameter(query: String, _)) :: Nil, None) =>
      if (query.isBlank)
        FinalResults(
          context, errors = CustomNodeError("Query is missing", Some(QueryParamName)) :: Nil, state = None)
      else
        withConnection(query) { statement =>
          val queryArgParams = toParameters(statement.getParameterMetaData())
          NextParameters(
            parameters = queryArgParams,
            state = Some(TransformationState(
              query = query,
              argsCount = queryArgParams.size,
              tableDef = TableDefinition(statement.getMetaData)
            )))
        }

    case TransformationStep(_, state@Some(TransformationState(_, _, tableDef))) =>
      val newCtxV = context.withVariable(
        name = OutputVariableNameDependency.extract(dependencies),
        value =  tableDef.resultSetType,
        paramName = None)
      FinalResults(
        finalContext = newCtxV.getOrElse(context),
        errors = newCtxV.swap.map(_.toList).getOrElse(Nil),
        state = state)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[TransformationState]): ServiceInvoker = {
    new ServiceInvoker {
      override val returnType: TypingResult =
        finalState.map(_.tableDef.resultSetType).getOrElse(typing.Unknown)

      override def invokeService(params: Map[String, Any])
                                (implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, contextId: ContextId): Future[Any] = {
        val state = finalState.get
        val results = new ArrayBuffer[TypedMap]()

        withConnection(state.query) { statement =>
          (1 to state.argsCount).foreach { argNo =>
            statement.setObject(argNo, params(s"$ArgPrefix$argNo"))
          }
          val resultSet = statement.executeQuery()
          resultSet.getFetchSize
          while (resultSet.next()) {
            val fields = state.tableDef.columnDefs.map { columnDef =>
              columnDef.name -> resultSet.getObject(columnDef.no)
            }.toMap
            results += TypedMap(fields)
          }
        }

        Future.successful { results.asJava }
      }
    }
  }

  private def toParameters(parameterMeta: ParameterMetaData): List[Parameter] =
    (1 to parameterMeta.getParameterCount).map { paramNo =>
      Parameter(s"$ArgPrefix$paramNo", typing.Unknown).copy(isLazyParameter = true)
    }.toList
}
