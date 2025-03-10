package pl.touk.nussknacker.engine.flink.table.source

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes.SetOf
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.definition.{FlinkDataDefinition, TablesDefinitionDiscovery}
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition._
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory.{
  AvailableTables,
  SelectedTable,
  TableSourceFactoryState
}
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory._

import scala.collection.compat.toTraversableLikeExtensionMethods

class TableSourceFactory(
    flinkDataDefinition: FlinkDataDefinition,
    testDataGenerationMode: TestDataGenerationMode
) extends SingleInputDynamicComponent[Source]
    with SourceFactory
    with LazyLogging {

  override def allowedProcessingModes: Component.AllowedProcessingModes =
    SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  override type State = TableSourceFactoryState

  private val miniclusterDependency                   = TypedNodeDependency[FlinkMiniClusterWithServices]
  override def nodeDependencies: List[NodeDependency] = List(miniclusterDependency)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val minicluster = miniclusterDependency.extract(dependencies)

      val (errors, tableDefinitions) = TablesDefinitionDiscovery
        .prepareDiscovery(
          flinkDataDefinition,
          minicluster
        )
        .orFail
        .listTables
        .map(_.toEither)
        .partitionMap(identity)
      errors.foreach(logger.warn("A validation error occured when trying to use configured tables", _))

      val tableNameParamDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)
      NextParameters(
        parameters = tableNameParamDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = Some(AvailableTables(tableDefinitions, minicluster))
      )
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil,
          Some(AvailableTables(tableDefinitions, discovery))
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)
      val initializer = new BasicContextInitializer(
        selectedTable.schema.toSourceRowDataType.getLogicalType.toTypingResult
      )
      FinalResults.forValidation(context, Nil, Some(SelectedTable(selectedTable, discovery)))(
        initializer.validationContext
      )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Source = {
    val (selectedTable, minicluster) = finalStateOpt match {
      case Some(SelectedTable(table, minicluster)) => table -> minicluster
      case _ =>
        throw new IllegalStateException(
          s"Unexpected final state determined during parameters validation: $finalStateOpt"
        )
    }
    new TableSource(selectedTable, flinkDataDefinition, testDataGenerationMode, minicluster)
  }

}

object TableSourceFactory {

  sealed trait TableSourceFactoryState

  private case class AvailableTables(tableDefinitions: List[TableDefinition], minicluster: FlinkMiniClusterWithServices)
      extends TableSourceFactoryState

  private case class SelectedTable(tableDefinition: TableDefinition, minicluster: FlinkMiniClusterWithServices)
      extends TableSourceFactoryState

}
