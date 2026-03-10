package pl.touk.nussknacker.engine.flink.table.io.source

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes.SetOf
import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.io.TableComponentFactoryUtils
import pl.touk.nussknacker.engine.flink.table.io.TableComponentFactoryUtils._
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.TableDiscovery
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.{TableUsageValidator, TableUseCase}
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, TableDefinition}
import pl.touk.nussknacker.engine.flink.table.io.source.TableSourceFactory.{
  AvailableTables,
  SelectedTable,
  TableSourceFactoryState
}
import pl.touk.nussknacker.engine.flink.table.io.source.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.typing.DataTypesExtensions.LogicalTypeExtension
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyValidationHandler

class TableSourceFactory(
    flinkDataDefinition: FlinkDataDefinition,
    testDataGenerationMode: TestDataGenerationMode,
    tableDiscovery: TableDiscovery,
    tableValidator: TableUsageValidator
) extends SingleInputDynamicComponent
    with WatermarkStrategyValidationHandler
    with SourceFactory
    with LazyLogging {

  override type Implementation = Source

  override type State = TableSourceFactoryState

  override def allowedProcessingModes: Component.AllowedProcessingModes =
    SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  private val streamEnvDependency                     = TypedNodeDependency[StreamExecutionEnvironment]
  override def nodeDependencies: List[NodeDependency] = List(streamEnvDependency)

  override def contextTransformation(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition =
    tableNameStep(inputContext, dependencies) orElse watermarkStrategyParametersStep(inputContext, dependencies)

  private def tableNameStep(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => {
      val sEnv           = streamEnvDependency.extract(dependencies)
      val env            = StreamTableEnvironment.create(sEnv)
      val tableIds       = tableDiscovery.discoverTableIdentifiers(flinkDataDefinition, env)
      val tableNameParam = TableComponentFactoryUtils.buildTableNameParam(tableIds).createParameter()
      NextParameters(
        parameters = tableNameParam :: Nil,
        errors = List.empty,
        state = Some(AvailableTables(tableIds, env))
      )
    }
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil,
          Some(AvailableTables(tableIds, env))
        ) => {
      val selectedTableId = TableComponentFactoryUtils.getSelectedTableIdUnsafe(tableName, tableIds)
      val selectedTable   = tableDiscovery.discoverTable(env, flinkDataDefinition, selectedTableId)
      val tableValidationErrors = tableValidator
        .validateTableUsage(selectedTable, TableUseCase.Source, env, flinkDataDefinition)
        .fold(
          errors =>
            errors.toList
              .map(err => CustomNodeError(err.message, Some(TableComponentFactoryUtils.tableNameParamName))),
          _ => List.empty
        )
      val nextState   = Some(SelectedTable(selectedTable, env))
      val initializer = new BasicContextInitializer(selectedTable.sourceRowDataType.getLogicalType.toTypingResult)
      NextParameters(
        prepareWatermarkStrategyParameters(
          initializer.validationContext(inputContext).getOrElse(inputContext),
          selectedTable.singleColumnWithTimezoneAwareRowtime
            .map(_.getName)
            .map(c => s"#input['$c']".spel)
            .getOrElse("".spel)
        ),
        errors = tableValidationErrors,
        state = nextState
      )
    }
  }

  override protected def resultAfterWatermarkStrategyParameters(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue]
  )(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(_, Some(selectedTableStateValue: SelectedTable)) =>
    val initializer = new BasicContextInitializer(
      selectedTableStateValue.tableDefinition.schema.toSourceRowDataType.getLogicalType.toTypingResult
    )
    FinalResults.forValidation(
      inputContext,
      state = Some(selectedTableStateValue)
    )(
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
    new TableSource(
      tableDefinition = selectedTable,
      flinkDataDefinition = flinkDataDefinition,
      testDataGenerationMode = testDataGenerationMode,
      environmentForTestingPurposes = env,
      watermarkStrategyOptions = extractWatermarkStrategyOptions(params)
    )
  }

}

object TableSourceFactory {

  sealed trait TableSourceFactoryState

  private case class AvailableTables(tableIds: List[ObjectIdentifier], env: StreamTableEnvironment)
      extends TableSourceFactoryState

  private case class SelectedTable(tableDefinition: TableDefinition, env: StreamTableEnvironment)
      extends TableSourceFactoryState

}
