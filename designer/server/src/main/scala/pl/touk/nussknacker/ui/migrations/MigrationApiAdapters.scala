package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.util.ApiAdapter

object MigrationApiAdapters {

  /*

    NOTE TO DEVELOPER:

    When adding adapters for new versions of MigrateScenarioData:

    1. Uncomment and implement a new case object for the adapter from V1 to the new version.
    2. Ensure the liftVersion function converts from the previous version to the new version.
    3. Implement the downgradeVersion function to convert from the new version back to the previous version.
    4. Update the adapters map to include the new adapter.
    5. Test the migration adapters thoroughly for correctness.
    6. Update StandardRemoteEnvironmentSpec, especially the Migrate endpoint mock

    Remember to uncomment the case object after implementation.
   */

  case object MigrationApiAdapterV1ToV2 extends ApiAdapter[MigrateScenarioData] {

    override def liftVersion: MigrateScenarioData => MigrateScenarioData = {
      case v1: MigrateScenarioDataV1 =>
        MigrateScenarioDataV2(
          sourceEnvironmentId = v1.sourceEnvironmentId,
          sourceScenarioVersionId = None,
          remoteUserName = v1.remoteUserName,
          processingMode = v1.processingMode,
          engineSetupName = v1.engineSetupName,
          processCategory = v1.processCategory,
          scenarioLabels = List.empty,
          scenarioGraph = v1.scenarioGraph,
          processName = v1.processName,
          isFragment = v1.isFragment
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

    override def downgradeVersion: MigrateScenarioData => MigrateScenarioData = {
      case v2: MigrateScenarioDataV2 =>
        MigrateScenarioDataV1(
          sourceEnvironmentId = v2.sourceEnvironmentId,
          remoteUserName = v2.remoteUserName,
          processingMode = v2.processingMode,
          engineSetupName = v2.engineSetupName,
          processCategory = v2.processCategory,
          scenarioGraph = v2.scenarioGraph,
          processName = v2.processName,
          isFragment = v2.isFragment
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

  }

  val adapters: Map[Int, ApiAdapter[MigrateScenarioData]] = Map(1 -> MigrationApiAdapterV1ToV2)

}
