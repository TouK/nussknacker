package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  MigrateScenarioRequestDto,
  MigrateScenarioRequestDtoV1,
  MigrateScenarioRequestDtoV2
}

sealed trait MigrateScenarioRequest {
  def currentVersion(): Int
}

object MigrateScenarioRequest {

  type CurrentMigrateScenarioRequest = MigrateScenarioRequestV2

  def toDomain(migrateScenarioRequestDto: MigrateScenarioRequestDto): MigrateScenarioRequest =
    migrateScenarioRequestDto match {
      case MigrateScenarioRequestDtoV1(
            version,
            sourceEnvironmentId,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        MigrateScenarioRequestV1(
          version,
          sourceEnvironmentId,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioGraph,
          processName,
          isFragment
        )
      case MigrateScenarioRequestDtoV2(
            version,
            sourceEnvironmentId,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        MigrateScenarioRequestV2(
          version,
          sourceEnvironmentId,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioGraph,
          processName,
          isFragment
        )
    }

  def fromDomain(migrateScenarioRequest: MigrateScenarioRequest): MigrateScenarioRequestDto =
    migrateScenarioRequest match {
      case MigrateScenarioRequestV1(
            version,
            sourceEnvironmentId,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        MigrateScenarioRequestDtoV1(
          version,
          sourceEnvironmentId,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioGraph,
          processName,
          isFragment
        )
      case MigrateScenarioRequestV2(
            version,
            sourceEnvironmentId,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        MigrateScenarioRequestDtoV2(
          version,
          sourceEnvironmentId,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioGraph,
          processName,
          isFragment
        )
    }

}

final case class MigrateScenarioRequestV1(
    version: Int,
    sourceEnvironmentId: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioRequest {
  override def currentVersion(): Int = version
}

final case class MigrateScenarioRequestV2(
    version: Int,
    sourceEnvironmentId: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioRequest {
  override def currentVersion(): Int = version
}
