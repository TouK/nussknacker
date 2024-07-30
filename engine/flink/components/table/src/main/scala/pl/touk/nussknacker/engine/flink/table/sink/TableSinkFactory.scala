package pl.touk.nussknacker.engine.flink.table.sink

import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, Validated}
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
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory._
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory.getSelectedTableUnsafe
import pl.touk.nussknacker.engine.flink.table.{TableDefinition, TableSqlDefinitions}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter
}

import scala.collection.immutable.ListMap

object TableSinkFactory {
  val valueParameterName: ParameterName = ParameterName("Value")
  private val rawValueParameterDeclaration =
    ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()

  private val tableNameParameterName      = TableSourceFactory.tableNameParamName
  val rawModeParameterName: ParameterName = ParameterName("Raw editor")

  private val rawModeParameterDeclaration = ParameterDeclaration
    .mandatory[Boolean](rawModeParameterName)
    .withCreator(c => c.copy(defaultValue = Some(Expression.spel("false")), editor = Some(BoolParameterEditor)))

  private val restrictedParamNamesForNonRawMode: Set[ParameterName] = Set(
    tableNameParameterName,
    rawModeParameterName
  )

}

final case class TransformationState(table: TableDefinition, valueParam: SchemaBasedParameter)

class TableSinkFactory(definition: TableSqlDefinitions)
    extends SingleInputDynamicComponent[Sink]
    with SinkFactory
    with BoundedStreamComponent {

  override type State = TransformationState

  private val tableNameParameterDeclaration = TableComponentFactory.buildTableNameParam(definition.tableDefinitions)

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
    // TODO local: extract based on param in state
    val lazyValueParam = rawValueParameterDeclaration.extractValueUnsafe(params)

    new TableSink(
      tableDefinition = finalState.table,
      sqlStatements = definition.sqlStatements,
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
      val selectedTable = getSelectedTableUnsafe(tableName, definition.tableDefinitions)

      val valueParameter = SingleSchemaBasedParameter(
        rawValueParameterDeclaration.createParameter(),
        TypingResultOutputValidator.validate(_, selectedTable.typingResult)
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
      val selectedTable = getSelectedTableUnsafe(tableName, definition.tableDefinitions)

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
  )(implicit nodeId: NodeId) = {
    val tableColumnValueParams =
      table.columns.map(c => {
        if (restrictedParamNamesForNonRawMode.contains(ParameterName(c.columnName))) {
          // TODO local: Deduplicate - Make a typed error common with avro one
          invalid(
            NonEmptyList.one(
              CustomNodeError(
                nodeId.id,
                s"Sink's output record's field name '${c.columnName}' is restricted. Please use raw editor for this case.",
                None
              )
            )
          )
        } else {
          val param: SchemaBasedParameter = SingleSchemaBasedParameter(
            value = Parameter(ParameterName(c.columnName), c.typingResult).copy(isLazyParameter = true),
            validator = TypingResultOutputValidator.validate(_, c.typingResult)
          )
          valid(c.columnName -> param)
        }
      })
    tableColumnValueParams.sequence.map(params => SchemaBasedRecordParameter(ListMap(params: _*)))
  }

}
