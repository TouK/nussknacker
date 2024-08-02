package pl.touk.nussknacker.engine.flink.table.sink

import pl.touk.nussknacker.engine.api.component.BoundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, ParameterDeclaration}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.LogicalTypesConversions.LogicalTypeConverter
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory.{rawValueParameterDeclaration, valueParameterName}
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory.getSelectedTableUnsafe
import pl.touk.nussknacker.engine.flink.table.{TableDefinition, TableSqlDefinitions}
import pl.touk.nussknacker.engine.util.parameters.SingleSchemaBasedParameter

object TableSinkFactory {
  // TODO: add non-raw value parameters
  val valueParameterName: ParameterName = ParameterName("Value")
  private val rawValueParameterDeclaration =
    ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()
}

class TableSinkFactory(definition: TableSqlDefinitions)
    extends SingleInputDynamicComponent[Sink]
    with SinkFactory
    with BoundedStreamComponent {

  override type State = TableDefinition

  private val tableNameParameterDeclaration = TableComponentFactory.buildTableNameParam(definition.tableDefinitions)
  private val tableNameParameterName        = tableNameParameterDeclaration.parameterName

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters =
          tableNameParameterDeclaration.createParameter() :: rawValueParameterDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (`tableNameParameterName`, DefinedEagerParameter(tableName: String, _)) ::
          (`valueParameterName`, rawValueParamValue) :: Nil,
          _
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, definition.tableDefinitions)

      val valueParameterTypeErrors = SingleSchemaBasedParameter(
        rawValueParameterDeclaration.createParameter(),
        TypingResultOutputValidator.validate(_, selectedTable.sinkRowDataType.getLogicalType.toTypingResult)
      ).validateParams(Map(valueParameterName -> rawValueParamValue)).fold(_.toList, _ => List.empty)

      FinalResults(context, valueParameterTypeErrors, Some(selectedTable))
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val lazyValueParam = rawValueParameterDeclaration.extractValueUnsafe(params)
    val selectedTable = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    new TableSink(selectedTable, definition.sqlStatements, lazyValueParam)
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

}
