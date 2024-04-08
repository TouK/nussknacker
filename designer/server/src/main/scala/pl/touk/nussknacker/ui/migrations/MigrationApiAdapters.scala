package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.util.ApiAdapter

object MigrationApiAdapters {

  case object MigrationApiAdapterV1ToV2 extends ApiAdapter[MigrateScenarioRequest] {

    override def liftVersion: MigrateScenarioRequest => MigrateScenarioRequest = {
      case v1: MigrateScenarioRequestV1 =>
        MigrateScenarioRequestV2(
          version = v1.version + 1,
          sourceEnvironmentId = v1.sourceEnvironmentId,
          processingMode = v1.processingMode,
          engineSetupName = v1.engineSetupName,
          processCategory = v1.processCategory,
          scenarioGraph = v1.scenarioGraph,
          processName = v1.processName,
          isFragment = v1.isFragment
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

    override def downgradeVersion: MigrateScenarioRequest => MigrateScenarioRequest = {
      case v2: MigrateScenarioRequestV2 =>
        MigrateScenarioRequestV1(
          version = v2.version - 1,
          sourceEnvironmentId = v2.sourceEnvironmentId,
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

  val adapters: Map[Int, ApiAdapter[MigrateScenarioRequest]] = Map(1 -> MigrationApiAdapterV1ToV2)

}
