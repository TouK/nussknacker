package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.api.MigrationApiEndpoints.Dtos.{MigrateScenarioRequestV1, MigrateScenarioRequestV2}

class MigrationApiAdapterService {

  def adaptToLowerVersion(migrateScenarioRequestV2: MigrateScenarioRequestV2): MigrateScenarioRequestV1 = ???

  def adaptToHigherVersion(migrateScenarioRequestV1: MigrateScenarioRequestV1): MigrateScenarioRequestV2 =
    MigrateScenarioRequestV2(
      sourceEnvironmentId = migrateScenarioRequestV1.sourceEnvironmentId,
      processingMode = migrateScenarioRequestV1.processingMode,
      engineSetupName = migrateScenarioRequestV1.engineSetupName,
      processCategory = migrateScenarioRequestV1.processCategory,
      processingType = migrateScenarioRequestV1.processingType,
      scenarioGraph = migrateScenarioRequestV1.scenarioGraph,
      processName = migrateScenarioRequestV1.processName,
      isFragment = migrateScenarioRequestV1.isFragment
    )

}
