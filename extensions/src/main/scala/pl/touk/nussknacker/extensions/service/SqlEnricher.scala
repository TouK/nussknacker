package pl.touk.nussknacker.extensions.service

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, typing}
import pl.touk.nussknacker.extensions.db.WithDBConnectionPool
import pl.touk.nussknacker.extensions.db.schema.TableDefinition

import java.sql.{ParameterMetaData, PreparedStatement}
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

  protected def initialStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                           (implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(initialParameters)
  }

  protected def queryParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                              (implicit nodeId: NodeId): NodeTransformationDefinition = {
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
        }  }

  protected def finalStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                         (implicit nodeId: NodeId): NodeTransformationDefinition = {
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

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition =
    initialStep(context, dependencies) orElse
      queryParamStep(context, dependencies) orElse
        finalStep(context, dependencies)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[TransformationState]): ServiceInvoker = {
    val state = finalState.get

    new ServiceInvoker {
      override val returnType: TypingResult = state.tableDef.resultSetType

      override def invokeService(params: Map[String, Any])
                                (implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, contextId: ContextId): Future[Any] = {
        val results = new ArrayBuffer[TypedMap]()

        withConnection(state.query) { statement =>
          setQueryArguments(statement, state.argsCount, params)
          val resultSet = statement.executeQuery()
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

  protected def toParameters(parameterMeta: ParameterMetaData): List[Parameter] =
    (1 to parameterMeta.getParameterCount).map { paramNo =>
      Parameter(s"$ArgPrefix$paramNo", typing.Unknown).copy(isLazyParameter = true)
    }.toList

  protected def setQueryArguments(statement: PreparedStatement,
                                  argsCount: Int,
                                  serviceInvokeParams: Map[String, Any]): Unit = {
    (1 to argsCount).foreach { argNo =>
      statement.setObject(argNo, serviceInvokeParams(s"$ArgPrefix$argNo"))
    }
  }
}
