package pl.touk.nussknacker.engine.flink.table.source

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.{SqlDataSourcesDefinition, TableDefinition}
import pl.touk.nussknacker.engine.flink.table.utils.SqlComponentFactory._

class SqlSourceFactory(definition: SqlDataSourcesDefinition)
    extends SingleInputDynamicComponent[Source]
    with SourceFactory {

  override type State = TableDefinition

  private val tableNameParamDeclaration = {
    val possibleTableParamValues = defs.tableDefinitions
      .map(c => FixedExpressionValue(s"'${c.tableName}'", c.tableName))
    ParameterDeclaration
      .mandatory[String](tableNameParamName)
      .withCreator(
        modify = _.copy(editor =
          Some(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: possibleTableParamValues))
        )
      )
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = tableNameParamDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep((`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil, _) =>
      val selectedTable = getSelectedTableUnsafe(tableName, definition.tableDefinitions)
      val typingResult  = selectedTable.typingResult
      val initializer   = new BasicContextInitializer(typingResult)
      FinalResults.forValidation(context, Nil, Some(selectedTable))(initializer.validationContext)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Source = {
    val selectedTable = finalStateOpt.getOrElse(
      throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation")
    )
    new SqlSource(selectedTable, definition.sqlStatements)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  override val allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

  private def getSelectedTableUnsafe(tableName: String): DataSourceTableDefinition =
    defs.tableDefinitions
      .find(_.tableName == tableName)
      .getOrElse(throw new IllegalStateException("Table with selected name not found."))

}

object SqlSourceFactory {
  val tableNameParamName: ParameterName = ParameterName("Table")
}
