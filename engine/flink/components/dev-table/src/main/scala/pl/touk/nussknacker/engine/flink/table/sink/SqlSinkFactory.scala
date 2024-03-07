package pl.touk.nussknacker.engine.flink.table.sink

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, ParameterWithExtractor, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.sink.SqlSinkFactory.ValueParamName
import pl.touk.nussknacker.engine.flink.table.utils.SqlComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.SqlComponentFactory.getSelectedTableUnsafe
import pl.touk.nussknacker.engine.flink.table.{SqlDataSourcesDefinition, TableDefinition}
import pl.touk.nussknacker.engine.util.parameters.SingleSchemaBasedParameter

object SqlSinkFactory {
  val ValueParamName = "Value"
}

class SqlSinkFactory(definition: SqlDataSourcesDefinition) extends SingleInputDynamicComponent[Sink] with SinkFactory {

  override type State = TableDefinition

  // TODO: add non-raw value parameters
  private val rawValueParam = ParameterWithExtractor.lazyMandatory[AnyRef](ValueParamName)

  private val tableNameParam: ParameterWithExtractor[String] =
    SqlComponentFactory.buildTableNameParam(definition.tableDefinitions)
  private val tableNameParamName = tableNameParam.parameter.name

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = rawValueParam.parameter :: tableNameParam.parameter :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep(
          (`ValueParamName`, rawValueParamValue) ::
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil,
          _
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, definition.tableDefinitions)

      val valueParameterTypeErrors = SingleSchemaBasedParameter(
        rawValueParam.parameter,
        TypingResultOutputValidator.validate(_, selectedTable.typingResult)
      ).validateParams(Map(ValueParamName -> rawValueParamValue)).fold(_.toList, _ => List.empty)

      FinalResults(context, valueParameterTypeErrors, Some(selectedTable))
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Sink = {
    val lazyValueParam = rawValueParam.extractValue(params)
    val selectedTable = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    new SqlSink(selectedTable, definition.sqlStatements, lazyValueParam)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  override val allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

}
