package pl.touk.nussknacker.engine.flink.table.sql

import cats.data.ValidatedNel
import cats.implicits._
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Table
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.AllProcessingModesComponent
import pl.touk.nussknacker.engine.api.context.OutputVar.CustomNodeFieldName
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import pl.touk.nussknacker.engine.flink.table.typing.ToTableTypeEncoder
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.jdk.CollectionConverters._
import scala.util.Try

object FlinkSqlQueryComponentFactory
    extends CustomStreamTransformer
    with SingleInputDynamicComponent
    with AllProcessingModesComponent {

  import pl.touk.nussknacker.engine.flink.table.typing.DataTypesExtensions.LogicalTypeExtension

  final case class SqlQuery(sql: String, outputType: TypingResult)
  final case class TransformationState(query: SqlQuery, input: QueryContextSchema)

  override type Implementation = FlinkCustomStreamTransformation
  override type State          = TransformationState

  private val streamEnvDependency = TypedNodeDependency[StreamExecutionEnvironment]
  private val nodeIdDependency    = TypedNodeDependency[NodeId]

  private val flinkSqlQueryParameterName: ParameterName = ParameterName("flinkSqlQuery")

  private val flinkSqlQueryParameter = ParameterDeclaration
    .mandatory[String](flinkSqlQueryParameterName)
    .withCreator(
      modify = _.copy(
        labelOpt = Some("Query"),
        editors = List(SqlParameterEditor),
        defaultValue = Some(Expression.spelTemplate("SELECT r.* FROM record r")),
        hintText = Some(
          "Flink SQL query starting with SELECT or WITH. Upstream variables are available as columns under 'record' table. " +
            "Record's timestamp is available under 'record_time' column in 'record' table."
        )
      )
    )
    .createParameter()

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): FlinkSqlQueryComponentFactory.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(parameters = flinkSqlQueryParameter :: Nil)
    case TransformationStep((`flinkSqlQueryParameterName`, DefinedEagerParameter(query: String, _)) :: Nil, _) => {
      val outputVariableName = OutputVariableNameDependency.extract(dependencies)

      val validationResult =
        validateBeforeProcessingQuery(context, query).andThen { _ =>
          val streamEnv      = streamEnvDependency.extract(dependencies)
          val streamTableEnv = StreamTableEnvironment.create(streamEnv)
          inferTypesAndValidate(context, query, streamTableEnv).toValidatedNel.map {
            case (inputTable, inferredOutputType) =>
              (
                Some(TransformationState(SqlQuery(query, inferredOutputType), inputTable)),
                inferredOutputType
              )
          }
        }

      val (errors, state, outputType) = validationResult.fold(
        errors => (errors.toList, None, Unknown),
        { case (state, outputType) => (Nil, state, outputType) }
      )

      FinalResults.forValidation(
        context,
        errors = errors,
        state = state
      )(
        _.clearVariables.withVariable(
          outputVariableName,
          value = outputType,
          paramName = Some(ParameterName(CustomNodeFieldName))
        )
      )
    }
  }

  private def validateBeforeProcessingQuery(context: ValidationContext, query: String)(
      implicit nodeId: NodeId
  ): ValidatedNel[CustomNodeError, Unit] = {
    val reservedVariableValidation =
      if (context.variables.contains(QueryContextSchema.recordTimeColumn)) {
        buildReservedEventTimeVariableError.invalidNel
      } else {
        ().validNel
      }

    val emptyQueryValidation =
      if (query.isBlank) {
        CustomNodeError("Query cannot be empty", Some(flinkSqlQueryParameterName)).invalidNel
      } else {
        ().validNel
      }

    (reservedVariableValidation, emptyQueryValidation).mapN((_, _) => ())
  }

  private def buildReservedEventTimeVariableError(implicit nodeId: NodeId): CustomNodeError =
    CustomNodeError(
      message =
        s"Variable '${QueryContextSchema.recordTimeColumn}' is reserved by Flink SQL component for record's timestamp handling. " +
          s"Please rename or remove this variable",
      paramName = None
    )

  private def inferTypesAndValidate(
      context: ValidationContext,
      query: String,
      streamTableEnv: StreamTableEnvironment
  )(implicit nodeId: NodeId): Either[CustomNodeError, (QueryContextSchema, TypingResult)] = {
    val queryContextSchema = QueryContextSchema.fromValidationContext(context)
    val emptyRowStream = streamTableEnv.toDataStream(
      streamTableEnv.fromValues(queryContextSchema.rowAlignedDataType, List.empty[Row].asJava)
    )
    streamTableEnv.createTemporaryView(
      QueryContextSchema.contextTableName,
      emptyRowStream,
      queryContextSchema.tableSchemaWithEventTime
    )
    val queryTable = Try(streamTableEnv.sqlQuery(query)).toEither.left.map(toNodeError)
    queryTable
      .flatMap(validateTableChangelogMode(_, streamTableEnv))
      .map { table =>
        val outputTypingResult = ToTableTypeEncoder.alignTypingResult(
          table.getResolvedSchema.toPhysicalRowDataType.getLogicalType.toTypingResult
        )
        queryContextSchema -> outputTypingResult
      }
  }

  private def validateTableChangelogMode(
      table: Table,
      streamTableEnv: StreamTableEnvironment
  )(implicit nodeId: NodeId) = Try(streamTableEnv.toDataStream(table)).toEither.left.map(toNodeError).map(_ => table)

  private def toNodeError(ex: Throwable)(implicit nodeId: NodeId) =
    CustomNodeError(Option(ex.getMessage).getOrElse(ex.toString), Some(flinkSqlQueryParameterName))

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): Implementation = {
    val TransformationState(query, inputTable) = finalState.getOrElse(
      throw new IllegalStateException(
        "Context transformation state was not properly passed to component's implementation."
      )
    )
    val nodeId = nodeIdDependency.extract(dependencies)
    new FlinkSqlQueryComponent(query, inputTable, nodeId)
  }

  override val nodeDependencies: List[NodeDependency] =
    List(OutputVariableNameDependency, streamEnvDependency, nodeIdDependency)
}
