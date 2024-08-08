package pl.touk.nussknacker.engine.flink.table.source

import pl.touk.nussknacker.engine.api.component.BoundedStreamComponent
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
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesConversions._
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.extractor.TablesExtractor
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory.tableNameParamName
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory._

class TableSourceFactory(
    sqlStatements: List[SqlStatement],
    testDataGenerationMode: TestDataGenerationMode
) extends SingleInputDynamicComponent[Source]
    with SourceFactory
    with BoundedStreamComponent {

  @transient
  private lazy val tableDefinitions = TablesExtractor.extractTablesFromFlinkRuntimeUnsafe(sqlStatements)

  override type State = TableDefinition

  private val tableNameParamDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)

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
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)
      val initializer = new BasicContextInitializer(
        selectedTable.schema.toSourceRowDataType.getLogicalType.toTypingResult
      )
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
    new TableSource(selectedTable, sqlStatements, testDataGenerationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

}

object TableSourceFactory {
  val tableNameParamName: ParameterName = ParameterName("Table")
}
