package pl.touk.nussknacker.engine.flink.util.service

import org.apache.commons.dbcp2.BasicDataSource
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, typing}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.util.db.TableDef

import java.sql.{Connection, ParameterMetaData}
import java.util.Collections
import scala.concurrent.{ExecutionContext, Future}

object SqlEnricher {

  final val ArgPrefix: String = "arg"

  final val QueryParamName: String = "Query"

  private val metaData = TypedNodeDependency(classOf[MetaData])

  private val QueryParam: Parameter = Parameter(QueryParamName, Typed[String]).copy(
    editor = Some(TextareaParameterEditor))

  case class TransformationState(query: String, argsCount: Int, tableDef: TableDef)
}

class SqlEnricher(connectionPool: BasicDataSource) extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {
  import SqlEnricher._

  private var conn: Connection = _

  override type State = TransformationState

  override val nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: metaData :: Nil

  override def open(jobData: JobData): Unit = {
    super.open(jobData)
    conn = connectionPool.getConnection
  }

  override def close(): Unit = {
    Option(conn).foreach(_.close())
    super.close()
  }

  override def initialParameters: List[Parameter] = QueryParam :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(initialParameters)

    case TransformationStep((QueryParamName, DefinedEagerParameter(query: String, _)) :: Nil, None) =>
      if (query.isBlank) {
        FinalResults(
          context, errors = CustomNodeError("Query is missing", Some(QueryParamName)) :: Nil, state = None)
      } else {
        val conn_ = connectionPool.getConnection
        try {
          val statement = conn_.prepareStatement(query)
          val queryArgParams = toParameters(statement.getParameterMetaData())
          NextParameters(
            parameters = queryArgParams,
            state = Some(TransformationState(
              query = query,
              argsCount = queryArgParams.size,
              tableDef = TableDef(statement.getMetaData)
            )))
        } finally { conn_.close() }
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
        val statement = conn.prepareStatement(state.query)
        (1 to state.argsCount).foreach { argNo =>
          statement.setObject(argNo, params(s"$ArgPrefix$argNo"))
        }
        val resultSet = statement.executeQuery()
        val results: java.util.List[TypedMap] = Collections.emptyList()
        while (resultSet.next()) {
          val fields = state.tableDef.columnDefs.map { columnDef =>
            columnDef.name -> resultSet.getObject(columnDef.no)
          }.toMap
          results.add(TypedMap(fields))
        }
        Future.successful { results }
      }
    }
  }

  private def toParameters(parameterMeta: ParameterMetaData): List[Parameter] =
    (1 to parameterMeta.getParameterCount).map { paramNo =>
      Parameter(s"$ArgPrefix$paramNo", typing.Unknown).copy(isLazyParameter = true)
    }.toList
}
