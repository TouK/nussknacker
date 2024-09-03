package pl.touk.nussknacker.engine.flink.table.sink

import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import pl.touk.nussknacker.engine.api.component.BoundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{BoolParameterEditor, NodeDependency, Parameter, ParameterDeclaration}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.DataDefinitionRegistrar._
import pl.touk.nussknacker.engine.flink.table.extractor.{DataDefinitionRegistrar, TablesDefinitionDiscovery}
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory._
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter
}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

class TableSinkFactory(dataDefinitionRegistrar: DataDefinitionRegistrar)
    extends SingleInputDynamicComponent[Sink]
    with SinkFactory
    with BoundedStreamComponent {

  @transient
  private lazy val tablesDiscovery = TablesDefinitionDiscovery.prepareDiscovery(dataDefinitionRegistrar).orFail

  override type State = TableSinkFactoryState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    prepareInitialParameters orElse
      rawModePrepareValueParameter orElse
      rawModeFinalStep(context) orElse
      nonRawModePrepareValueParameters(context) orElse
      nonRawModeValidateValueParametersFinalStep(context)
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

  private lazy val prepareInitialParameters: ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    val tableDefinitions          = tablesDiscovery.listTables
    val tableNameParamDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)
    NextParameters(
      parameters = tableNameParamDeclaration.createParameter() :: rawModeParameterDeclaration.createParameter() :: Nil,
      errors = List.empty,
      state = Some(AvailableTables(tableDefinitions))
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
          Some(AvailableTables(tableDefinitions))
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)

      val valueParameter = SingleSchemaBasedParameter(
        rawValueParameterDeclaration.createParameter(),
        TableTypeOutputValidator.validate(_, selectedTable.sinkRowDataType.getLogicalType)
      )
      val valueParameterTypeErrors =
        valueParameter.validateParams(Map(valueParameterName -> rawValueParamValue)).fold(_.toList, _ => List.empty)

      FinalResults(ctx, valueParameterTypeErrors, Some(SelectedTableWithValueParam(selectedTable, valueParameter)))
  }

  private def nonRawModePrepareValueParameters(
      ctx: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(false, _)) :: Nil,
          Some(AvailableTables(tableDefinitions))
        ) => {
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)

      val tableValueParamValidation = buildNonRawValueParameter(selectedTable)

      tableValueParamValidation match {
        case Validated.Valid(valueParam) =>
          NextParameters(
            valueParam.toParameters,
            Nil,
            Some(SelectedTableWithValueParam(selectedTable, valueParam))
          )
        case Validated.Invalid(errors) => {
          FinalResults(
            ctx,
            errors.toList,
            None
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

  private def buildNonRawValueParameter(
      table: TableDefinition
  )(implicit nodeId: NodeId): ValidatedNel[CustomNodeError, SchemaBasedRecordParameter] = {
    val tableColumnValueParams =
      table.sinkRowDataType.toLogicalRowTypeUnsafe.getFields.asScala.toList.map(field => {
        if (restrictedParamNamesForNonRawMode.contains(ParameterName(field.getName))) {
          invalid(
            NonEmptyList.one(
              CustomNodeError(
                nodeId.id,
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
      dataDefinitionRegistrar = dataDefinitionRegistrar,
      value = lazyValueParam
    )
  }

}

object TableSinkFactory {

  private val valueParameterName: ParameterName = ParameterName("Value")
  private val rawValueParameterDeclaration =
    ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()

  private val rawModeParameterName: ParameterName = ParameterName("Raw editor")

  private val rawModeParameterDeclaration = ParameterDeclaration
    .mandatory[Boolean](rawModeParameterName)
    .withCreator(c => c.copy(defaultValue = Some(Expression.spel("false")), editor = Some(BoolParameterEditor)))

  private val restrictedParamNamesForNonRawMode: Set[ParameterName] = Set(
    tableNameParamName,
    rawModeParameterName
  )

  sealed trait TableSinkFactoryState

  private case class AvailableTables(tableDefinitions: List[TableDefinition]) extends TableSinkFactoryState

  private case class SelectedTableWithValueParam(tableDefinition: TableDefinition, valueParam: SchemaBasedParameter)
      extends TableSinkFactoryState

}
