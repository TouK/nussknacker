package pl.touk.nussknacker.engine.flink.table.source

import pl.touk.nussknacker.engine.api.component.BoundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.{FlinkDataDefinition, TablesDefinitionDiscovery}
import pl.touk.nussknacker.engine.flink.table.extractor.FlinkDataDefinition._
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory.{
  AvailableTables,
  SelectedTable,
  TableSourceFactoryState
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory._

class TableSourceFactory(
    flinkDataDefinition: FlinkDataDefinition,
    testDataGenerationMode: TestDataGenerationMode
) extends SingleInputDynamicComponent[Source]
    with SourceFactory
    with BoundedStreamComponent {

  @transient
  private lazy val tablesDiscovery = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).orFail

  override type State = TableSourceFactoryState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val tableDefinitions          = tablesDiscovery.listTables
      val tableNameParamDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)
      NextParameters(
        parameters = tableNameParamDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = Some(AvailableTables(tableDefinitions))
      )
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil,
          Some(AvailableTables(tableDefinitions))
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)
      val initializer = new BasicContextInitializer(
        selectedTable.schema.toSourceRowDataType.getLogicalType.toTypingResult
      )
      FinalResults.forValidation(context, Nil, Some(SelectedTable(selectedTable)))(initializer.validationContext)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Source = {
    val selectedTable = finalStateOpt match {
      case Some(SelectedTable(table)) => table
      case _ =>
        throw new IllegalStateException(
          s"Unexpected final state determined during parameters validation: $finalStateOpt"
        )
    }
    new TableSource(selectedTable, flinkDataDefinition, testDataGenerationMode)
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

}

object TableSourceFactory {

  sealed trait TableSourceFactoryState

  private case class AvailableTables(tableDefinitions: List[TableDefinition]) extends TableSourceFactoryState

  private case class SelectedTable(tableDefinition: TableDefinition) extends TableSourceFactoryState

}
