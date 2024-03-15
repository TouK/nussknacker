package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.api.MigrationApiEndpoints.Dtos.{MigrateScenarioRequestV1, MigrateScenarioRequestV2}

class MigrationApiAdapterService {

  def adaptToLowerVersion(migrateScenarioRequestV2: MigrateScenarioRequestV2): MigrateScenarioRequestV1 =
    MigrateScenarioRequestV1(
      sourceEnvironmentId = migrateScenarioRequestV2.sourceEnvironmentId,
      processingMode = migrateScenarioRequestV2.processingMode,
      engineSetupName = migrateScenarioRequestV2.engineSetupName,
      processCategory = migrateScenarioRequestV2.processCategory,
      scenarioGraph = migrateScenarioRequestV2.scenarioGraph,
      processName = migrateScenarioRequestV2.processName,
      isFragment = migrateScenarioRequestV2.isFragment
    )

  def adaptToHigherVersion(migrateScenarioRequestV1: MigrateScenarioRequestV1): MigrateScenarioRequestV2 =
    MigrateScenarioRequestV2(
      sourceEnvironmentId = migrateScenarioRequestV1.sourceEnvironmentId,
      processingMode = migrateScenarioRequestV1.processingMode,
      engineSetupName = migrateScenarioRequestV1.engineSetupName,
      processCategory = migrateScenarioRequestV1.processCategory,
      scenarioGraph = migrateScenarioRequestV1.scenarioGraph,
      processName = migrateScenarioRequestV1.processName,
      isFragment = migrateScenarioRequestV1.isFragment
    )

}
