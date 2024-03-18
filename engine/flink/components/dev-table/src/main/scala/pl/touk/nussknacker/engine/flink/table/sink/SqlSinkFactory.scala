package pl.touk.nussknacker.engine.flink.table.sink

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, ParameterDeclaration, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.sink.SqlSinkFactory.{rawValueParameterDeclaration, valueParameterName}
import pl.touk.nussknacker.engine.flink.table.utils.SqlComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.SqlComponentFactory.getSelectedTableUnsafe
import pl.touk.nussknacker.engine.flink.table.{SqlDataSourcesDefinition, TableDefinition}
import pl.touk.nussknacker.engine.util.parameters.SingleSchemaBasedParameter

object SqlSinkFactory {
  // TODO: add non-raw value parameters
  val valueParameterName: ParameterName = ParameterName("Value")
  private val rawValueParameterDeclaration =
    ParameterDeclaration.lazyMandatory[AnyRef](valueParameterName).withCreator()
}

class SqlSinkFactory(definition: SqlDataSourcesDefinition) extends SingleInputDynamicComponent[Sink] with SinkFactory {

  override type State = TableDefinition

  private val tableNameParameterDeclaration = SqlComponentFactory.buildTableNameParam(definition.tableDefinitions)
  private val tableNameParameterName        = tableNameParameterDeclaration.parameterName

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters =
          rawValueParameterDeclaration.createParameter() :: tableNameParameterDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (`valueParameterName`, rawValueParamValue) ::
          (`tableNameParameterName`, DefinedEagerParameter(tableName: String, _)) :: Nil,
          _
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, definition.tableDefinitions)

      val valueParameterTypeErrors = SingleSchemaBasedParameter(
        rawValueParameterDeclaration.createParameter(),
        TypingResultOutputValidator.validate(_, selectedTable.typingResult)
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
    new SqlSink(selectedTable, definition.sqlStatements, lazyValueParam)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  override val allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

}
