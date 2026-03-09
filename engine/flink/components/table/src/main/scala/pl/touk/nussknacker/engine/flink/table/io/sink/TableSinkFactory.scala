package pl.touk.nussknacker.engine.flink.table.io.sink

import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes.SetOf
import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.io.TableComponentFactoryUtils
import pl.touk.nussknacker.engine.flink.table.io.TableComponentFactoryUtils._
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.TableDiscovery
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.{TableUsageValidator, TableUseCase}
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, TableDefinition}
import pl.touk.nussknacker.engine.flink.table.io.sink.TableSinkFactory._
import pl.touk.nussknacker.engine.flink.table.typing.DataTypesExtensions._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter
}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

class TableSinkFactory(
    flinkDataDefinition: FlinkDataDefinition,
    tableDiscovery: TableDiscovery,
    tableValidator: TableUsageValidator
) extends SingleInputDynamicComponent
    with SinkFactory
    with LazyLogging {

  override type Implementation = Sink

  override type State = TableSinkFactoryState

  override def allowedProcessingModes: Component.AllowedProcessingModes =
    SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    prepareInitialParameters(dependencies) orElse
      rawModePrepareValueParameter orElse
      rawModeFinalStep(context) orElse
      nonRawModePrepareValueParameters(context) orElse
      nonRawModeValidateValueParametersFinalStep(context)
  }

  private val streamEnvDependency                     = TypedNodeDependency[StreamExecutionEnvironment]
  override def nodeDependencies: List[NodeDependency] = List(streamEnvDependency)

  private def prepareInitialParameters(dependencies: List[NodeDependencyValue]): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val sEnv           = streamEnvDependency.extract(dependencies)
      val env            = StreamTableEnvironment.create(sEnv)
      val tableIds       = tableDiscovery.discoverTableIdentifiers(flinkDataDefinition, env)
      val tableNameParam = TableComponentFactoryUtils.buildTableNameParam(tableIds).createParameter()
      NextParameters(
        parameters = tableNameParam :: rawModeParameterDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = Some(AvailableTables(tableIds, env))
      )
  }

  private lazy val rawModePrepareValueParameter: ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParamName`, _) ::
          (`rawModeParameterName`, DefinedEagerParameter(true, _)) :: Nil,
          state
        ) =>
      NextParameters(rawValueParameterDeclaration.createParameter() :: Nil, state = state)
  }

  private def rawModeFinalStep(ctx: ValidationContext)(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(true, _)) ::
          (`valueParameterName`, rawValueParamValue) :: Nil,
          Some(AvailableTables(tableIds, env))
        ) =>
      val (selectedTable, tableValidationErrors) = discoverAndValidateTable(tableName, tableIds, env)

      val valueParameter = SingleSchemaBasedParameter(
        rawValueParameterDeclaration.createParameter(),
        TableTypeOutputValidator.validate(_, selectedTable.sinkRowDataType.getLogicalType)
      )
      val valueParameterTypeErrors =
        valueParameter.validateParams(Map(valueParameterName -> rawValueParamValue)).fold(_.toList, _ => List.empty)

      FinalResults(
        finalContext = ctx,
        errors = valueParameterTypeErrors ++ tableValidationErrors,
        state = Some(SelectedTableWithValueParam(selectedTable, valueParameter))
      )
  }

  private def nonRawModePrepareValueParameters(
      ctx: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(false, _)) :: Nil,
          Some(AvailableTables(tableIds, env))
        ) => {
      val (selectedTable, tableValidationErrors) = discoverAndValidateTable(tableName, tableIds, env)
      val tableValueParamValidation              = buildNonRawValueParameter(selectedTable)

      tableValueParamValidation match {
        case Validated.Valid(valueParam) =>
          NextParameters(
            valueParam.toParameters,
            tableValidationErrors,
            Some(SelectedTableWithValueParam(selectedTable, valueParam))
          )
        case Validated.Invalid(errors) => {
          FinalResults(
            finalContext = ctx,
            errors = errors.toList ++ tableValidationErrors,
            state = None
          )
        }
      }
    }
  }

  private def nonRawModeValidateValueParametersFinalStep(
      ctx: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(_, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(false, _)) ::
          valueParams,
          state @ Some(SelectedTableWithValueParam(_, valueParam))
        ) =>
      val errors = valueParam.validateParams(valueParams.toMap).fold(err => err.toList, _ => Nil)

      FinalResults(ctx, errors, state)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val finalState = finalStateOpt match {
      case Some(selectedTableWithValueParam: SelectedTableWithValueParam) => selectedTableWithValueParam
      case _ =>
        throw new IllegalStateException(
          s"Unexpected final state determined during parameters validation: $finalStateOpt"
        )
    }
    val lazyValueParam = SinkValue.applyUnsafe(finalState.valueParam, params).toLazyParameter

    new TableSink(
      tableDefinition = finalState.tableDefinition,
      flinkDataDefinition = flinkDataDefinition,
      value = lazyValueParam
    )
  }

  private def discoverAndValidateTable(tableName: String, tableIds: List[ObjectIdentifier], env: TableEnvironment)(
      implicit nodeId: NodeId
  ) = {
    val selectedTableId = TableComponentFactoryUtils.getSelectedTableIdUnsafe(tableName, tableIds)
    val selectedTable   = tableDiscovery.discoverTable(env, flinkDataDefinition, selectedTableId)
    val tableValidationErrors = tableValidator
      .validateTableUsage(selectedTable, TableUseCase.Sink, env, flinkDataDefinition)
      .fold(
        errors =>
          errors.toList
            .map(err => CustomNodeError(err.message, Some(TableComponentFactoryUtils.tableNameParamName))),
        _ => List.empty
      )
    (selectedTable, tableValidationErrors)
  }

  private def buildNonRawValueParameter(
      table: TableDefinition
  )(implicit nodeId: NodeId): ValidatedNel[CustomNodeError, SchemaBasedRecordParameter] = {
    val tableColumnValueParams =
      table.sinkRowDataType.toLogicalRowTypeUnsafe.getFields.asScala.toList.map(field => {
        if (restrictedParamNamesForNonRawMode.contains(ParameterName(field.getName))) {
          invalid(
            NonEmptyList.one(
              CustomNodeError(
                nodeId,
                s"Sink's output record's field name '${field.getName}' is restricted. Please use raw editor for this case.",
                None
              )
            )
          )
        } else {
          val param: SchemaBasedParameter = SingleSchemaBasedParameter(
            value = Parameter(ParameterName(field.getName), field.getType.toTypingResult).copy(isLazyParameter = true),
            validator = TableTypeOutputValidator.validate(_, field.getType)
          )
          valid(field.getName -> param)
        }
      })
    tableColumnValueParams.sequence.map(params => SchemaBasedRecordParameter(ListMap(params: _*)))
  }

}

object TableSinkFactory {

  private val valueParameterName: ParameterName = ParameterName("Value")
  private val rawValueParameterDeclaration =
    ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()

  private val rawModeParameterName: ParameterName = ParameterName("Raw editor")

  private val rawModeParameterDeclaration = ParameterDeclaration
    .mandatory[Boolean](rawModeParameterName)
    .withCreator(c => c.copy(defaultValue = Some(Expression.spel("false")), editors = List(BoolParameterEditor)))

  private val restrictedParamNamesForNonRawMode: Set[ParameterName] = Set(
    tableNameParamName,
    rawModeParameterName
  )

  sealed trait TableSinkFactoryState

  private case class AvailableTables(tableIds: List[ObjectIdentifier], env: TableEnvironment)
      extends TableSinkFactoryState

  private case class SelectedTableWithValueParam(tableDefinition: TableDefinition, valueParam: SchemaBasedParameter)
      extends TableSinkFactoryState

}
