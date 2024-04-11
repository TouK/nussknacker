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
import pl.touk.nussknacker.ui.util.VersionedData

sealed trait MigrateScenarioData extends VersionedData

object MigrateScenarioData {

  type CurrentMigrateScenarioRequest = MigrateScenarioDataV2

  def toDomain(migrateScenarioRequestDto: MigrateScenarioRequestDto): MigrateScenarioData =
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
        MigrateScenarioDataV1(
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
        MigrateScenarioDataV2(
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

  def fromDomain(migrateScenarioRequest: MigrateScenarioData): MigrateScenarioRequestDto =
    migrateScenarioRequest match {
      case MigrateScenarioDataV1(
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
      case MigrateScenarioDataV2(
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

final case class MigrateScenarioDataV1(
    version: Int,
    sourceEnvironmentId: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioData {
  override def currentVersion(): Int = version
}

final case class MigrateScenarioDataV2(
    version: Int,
    sourceEnvironmentId: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioData {
  override def currentVersion(): Int = version
}
