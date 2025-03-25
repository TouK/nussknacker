package pl.touk.nussknacker.engine.flink.table.source

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
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
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.definition.{
  FlinkDataDefinition,
  FlinkDataDefinitionError,
  TablesDefinitionDiscovery
}
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
    with LazyLogging {

  override def allowedProcessingModes: Component.AllowedProcessingModes =
    SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  override type State = TableSourceFactoryState

  // TODO: StreamExecutionEnvironment or StreamTableEnvironment? StreamTableEnvironment would be better because we
  //  convert it into it in almost every case and it should be replaceable
  // TODO: we need some wrapper to clean the env but its not so simple
  private val streamEnvDependency                     = TypedNodeDependency[StreamExecutionEnvironment]
  override def nodeDependencies: List[NodeDependency] = List(streamEnvDependency)

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val streamEnv = streamEnvDependency.extract(dependencies)
      val streamTableEnv = StreamTableEnvironment.create(
        streamEnv,
        EnvironmentSettings
          .newInstance()
          .withConfiguration(Configuration.fromMap(streamEnv.getConfiguration.toMap))
          .build()
      )

      val (errors, tableDefinitions) = TablesDefinitionDiscovery
        .discoverTables(
          flinkDataDefinition,
          streamTableEnv
        )
        .foldLeft((List.empty[FlinkDataDefinitionError], List.empty[TableDefinition])) {
          case ((errs, tables), Validated.Invalid(errsNel)) =>
            (errs ++ errsNel.toList, tables)
          case ((errs, tables), Validated.Valid(table)) =>
            (errs, table :: tables)
        }
      errors.foreach(logger.warn("A validation error occured when trying to use configured tables", _))

      val tableNameParamDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)
      NextParameters(
        parameters = tableNameParamDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = Some(AvailableTables(tableDefinitions, streamTableEnv))
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
    val (selectedTable, env) = finalStateOpt match {
      case Some(SelectedTable(table, env)) => table -> env
      case _ =>
        throw new IllegalStateException(
          s"Unexpected final state determined during parameters validation: $finalStateOpt"
        )
    }
    new TableSource(selectedTable, flinkDataDefinition, testDataGenerationMode, env)
  }

}

object TableSourceFactory {

  sealed trait TableSourceFactoryState

  private case class AvailableTables(tableDefinitions: List[TableDefinition], env: StreamTableEnvironment)
      extends TableSourceFactoryState

  private case class SelectedTable(tableDefinition: TableDefinition, env: StreamTableEnvironment)
      extends TableSourceFactoryState

}
