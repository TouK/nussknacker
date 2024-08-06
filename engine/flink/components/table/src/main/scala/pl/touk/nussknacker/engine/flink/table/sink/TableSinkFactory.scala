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
import pl.touk.nussknacker.engine.flink.table.LogicalTypesConversions.LogicalTypeConverter
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.extractor.TablesExtractor
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory._
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory.getSelectedTableUnsafe
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter
}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValue

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object TableSinkFactory {
  private val valueParameterName: ParameterName = ParameterName("Value")
  private val rawValueParameterDeclaration =
    ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()

  private val tableNameParameterName              = TableSourceFactory.tableNameParamName
  private val rawModeParameterName: ParameterName = ParameterName("Raw editor")

  private val rawModeParameterDeclaration = ParameterDeclaration
    .mandatory[Boolean](rawModeParameterName)
    .withCreator(c => c.copy(defaultValue = Some(Expression.spel("false")), editor = Some(BoolParameterEditor)))

  private val restrictedParamNamesForNonRawMode: Set[ParameterName] = Set(
    tableNameParameterName,
    rawModeParameterName
  )

}

final case class TransformationState(table: TableDefinition, valueParam: SchemaBasedParameter)

class TableSinkFactory(sqlStatements: List[SqlStatement])
    extends SingleInputDynamicComponent[Sink]
    with SinkFactory
    with BoundedStreamComponent {

  @transient
  private lazy val tableDefinitions = TablesExtractor.extractTablesFromFlinkRuntimeUnsafe(sqlStatements)

  override type State = TransformationState

  private val tableNameParameterDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    prepareInitialParameters orElse
      rawModePrepareValueParameter orElse
      rawModeFinalStep(context) orElse
      nonRawModePrepareValueParameters(context) orElse
      nonRawModeValidateValueParametersFinalStep(context)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val finalState = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    val lazyValueParam = SinkValue.applyUnsafe(finalState.valueParam, params).toLazyParameter

    new TableSink(
      tableDefinition = finalState.table,
      sqlStatements = sqlStatements,
      value = lazyValueParam
    )
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

  private lazy val prepareInitialParameters: ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(
      parameters =
        tableNameParameterDeclaration.createParameter() :: rawModeParameterDeclaration.createParameter() :: Nil,
      errors = List.empty,
      state = None
    )
  }

  private lazy val rawModePrepareValueParameter: ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParameterName`, _) ::
          (`rawModeParameterName`, DefinedEagerParameter(true, _)) :: Nil,
          _
        ) =>
      NextParameters(rawValueParameterDeclaration.createParameter() :: Nil)
  }

  private def rawModeFinalStep(ctx: ValidationContext)(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParameterName`, DefinedEagerParameter(tableName: String, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(true, _)) ::
          (`valueParameterName`, rawValueParamValue) :: Nil,
          _
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)

      val valueParameter = SingleSchemaBasedParameter(
        rawValueParameterDeclaration.createParameter(),
        TypingResultOutputValidator.validate(_, selectedTable.schema.toSinkRowDataType.getLogicalType.toTypingResult)
      )
      val valueParameterTypeErrors =
        valueParameter.validateParams(Map(valueParameterName -> rawValueParamValue)).fold(_.toList, _ => List.empty)

      FinalResults(ctx, valueParameterTypeErrors, Some(TransformationState(selectedTable, valueParameter)))
  }

  private def nonRawModePrepareValueParameters(
      ctx: ValidationContext
  )(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`tableNameParameterName`, DefinedEagerParameter(tableName: String, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(false, _)) :: Nil,
          _
        ) => {
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)

      val tableValueParamValidation = buildNonRawValueParameter(selectedTable)

      tableValueParamValidation match {
        case Validated.Valid(valueParam) =>
          NextParameters(
            valueParam.toParameters,
            Nil,
            Some(TransformationState(selectedTable, valueParam))
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
          (`tableNameParameterName`, DefinedEagerParameter(_, _)) ::
          (`rawModeParameterName`, DefinedEagerParameter(false, _)) ::
          valueParams,
          Some(tState)
        ) =>
      val errors = tState.valueParam.validateParams(valueParams.toMap).fold(err => err.toList, _ => Nil)

      FinalResults(ctx, errors, Some(tState))
  }

  private def buildNonRawValueParameter(
      table: TableDefinition
  )(implicit nodeId: NodeId): ValidatedNel[CustomNodeError, SchemaBasedRecordParameter] = {
    val tableColumnValueParams =
      table.schema.toSinkRowDataType.getLogicalType.toRowTypeUnsafe.getFields.asScala.toList.map(field => {
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
            validator = TypingResultOutputValidator.validate(_, field.getType.toTypingResult)
          )
          valid(field.getName -> param)
        }
      })
    tableColumnValueParams.sequence.map(params => SchemaBasedRecordParameter(ListMap(params: _*)))
  }

}
