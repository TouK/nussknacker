package pl.touk.nussknacker.engine.flink.table.source

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.component.{Component, ProcessingMode}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes.SetOf
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedSingleParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
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
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyValidationHandler

class TableSourceFactory(
    flinkDataDefinition: FlinkDataDefinition,
    testDataGenerationMode: TestDataGenerationMode
) extends SingleInputDynamicComponent
    with WatermarkStrategyValidationHandler
    with SourceFactory
    with LazyLogging {

  override def allowedProcessingModes: Component.AllowedProcessingModes =
    SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  override type Implementation = Source

  override type State = TableSourceFactoryState

  // TODO: StreamExecutionEnvironment or StreamTableEnvironment? StreamTableEnvironment would be better because we
  //  convert it into it in almost every case and it should be replaceable
  // TODO: we need some wrapper to clean the env but its not so simple
  private val streamEnvDependency                     = TypedNodeDependency[StreamExecutionEnvironment]
  override def nodeDependencies: List[NodeDependency] = List(streamEnvDependency)

  override def contextTransformation(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    tableNameStep(inputContext, dependencies) orElse watermarkStrategyParametersStep(inputContext, dependencies)

  private def tableNameStep(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val streamEnv      = streamEnvDependency.extract(dependencies)
      val streamTableEnv = StreamTableEnvironment.create(streamEnv)

      val (errors, tableDefinitions) = new TablesDefinitionDiscovery(streamTableEnv)
        .discoverTables(flinkDataDefinition)
        .foldLeft((List.empty[FlinkDataDefinitionError], List.empty[TableDefinition])) {
          case ((errs, tables), Validated.Invalid(errsNel)) =>
            (errs ++ errsNel.toList, tables)
          case ((errs, tables), Validated.Valid(table)) =>
            (errs, table :: tables)
        }
      errors.foreach(logger.warn("A validation error occurred when trying to use configured tables", _))

      val tableNameParamDeclaration = TableComponentFactory.buildTableNameParam(tableDefinitions)
      NextParameters(
        parameters = tableNameParamDeclaration.createParameter() :: Nil,
        errors = List.empty,
        state = Some(AvailableTables(tableDefinitions, streamTableEnv))
      )
    case TransformationStep(
          (`tableNameParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil,
          Some(AvailableTables(tableDefinitions, env))
        ) =>
      val selectedTable = getSelectedTableUnsafe(tableName, tableDefinitions)
      val initializer = new BasicContextInitializer(
        selectedTable.schema.toSourceRowDataType.getLogicalType.toTypingResult
      )
      val nextState = Some(SelectedTable(selectedTable, env))
      NextParameters(
        prepareWatermarkStrategyParameters(
          initializer.validationContext(inputContext).getOrElse(ValidationContext.empty),
          selectedTable.singleColumnWithTimezoneAwareRowtime
            .map(_.getName)
            .map(c => s"#input['$c']".spel)
            .getOrElse("".spel)
        ),
        state = nextState
      )
  }

  override protected def resultAfterWatermarkStrategyParameters(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue],
      parameters: List[(ParameterName, DefinedSingleParameter)],
      state: Option[TableSourceFactoryState]
  )(implicit nodeId: NodeId): TransformationStepResult = {
    val selectedTableStateValue = state match {
      case Some(selectedTableState: SelectedTable) =>
        selectedTableState
      case other =>
        throw new IllegalStateException(s"Unexpected state [$other] after watermark strategy parameters step")
    }
    val initializer = new BasicContextInitializer(
      selectedTableStateValue.tableDefinition.schema.toSourceRowDataType.getLogicalType.toTypingResult
    )
    FinalResults.forValidation(
      inputContext,
      errors = Nil,
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

  private case class AvailableTables(tableDefinitions: List[TableDefinition], env: StreamTableEnvironment)
      extends TableSourceFactoryState

  private case class SelectedTable(tableDefinition: TableDefinition, env: StreamTableEnvironment)
      extends TableSourceFactoryState

}
