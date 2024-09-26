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
import pl.touk.nussknacker.ui.migrations.MigrationService.MigrationError
import pl.touk.nussknacker.ui.util.VersionedData

sealed trait MigrateScenarioData extends VersionedData

object MigrateScenarioData {

  type CurrentMigrateScenarioData = MigrateScenarioDataV2

  def toDomain(migrateScenarioRequestDto: MigrateScenarioRequestDto): Either[MigrationError, MigrateScenarioData] =
    migrateScenarioRequestDto match {
      case MigrateScenarioRequestDtoV1(
            1,
            sourceEnvironmentId,
            remoteUserName,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        Right(
          MigrateScenarioDataV1(
            sourceEnvironmentId,
            remoteUserName,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          )
        )
      case MigrateScenarioRequestDtoV2(
            2,
            sourceEnvironmentId,
            remoteUserName,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioLabels,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        Right(
          MigrateScenarioDataV2(
            sourceEnvironmentId,
            remoteUserName,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioLabels,
            scenarioGraph,
            processName,
            isFragment
          )
        )
      case _ => Left(MigrationError.CannotTransformMigrateScenarioRequestIntoMigrationDomain)
    }

  def fromDomain(migrateScenarioRequest: MigrateScenarioData): MigrateScenarioRequestDto =
    migrateScenarioRequest match {
      case dataV1 @ MigrateScenarioDataV1(
            sourceEnvironmentId,
            remoteUserName,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        MigrateScenarioRequestDtoV1(
          version = dataV1.currentVersion,
          sourceEnvironmentId,
          remoteUserName,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioGraph,
          processName,
          isFragment
        )
      case dataV2 @ MigrateScenarioDataV2(
            sourceEnvironmentId,
            remoteUserName,
            processingMode,
            engineSetupName,
            processCategory,
            scenarioLabels,
            scenarioGraph,
            processName,
            isFragment
          ) =>
        MigrateScenarioRequestDtoV2(
          version = dataV2.currentVersion,
          sourceEnvironmentId,
          remoteUserName,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioLabels,
          scenarioGraph,
          processName,
          isFragment
        )
    }

}

final case class MigrateScenarioDataV1(
    sourceEnvironmentId: String,
    remoteUserName: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioData {
  override val currentVersion: Int = 1
}

final case class MigrateScenarioDataV2(
    sourceEnvironmentId: String,
    remoteUserName: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioLabels: List[String],
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioData {
  override val currentVersion: Int = 2
}

/*

NOTE TO DEVELOPER:

When implementing MigrateScenarioRequestDtoV3:

1. Review and update the parameter types and names if necessary.
2. Consider backward compatibility with existing code.
3. Update the encoder and decoder accordingly.
4. Check if any adapters or converters need modification.
5. Add any necessary documentation or comments.

Remember to uncomment the class definition after implementation.

final case class MigrateScenarioDataV3(
    sourceEnvironmentId: String,
    remoteUserName: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    processCategory: String,
    scenarioGraph: ScenarioGraph,
    processName: ProcessName,
    isFragment: Boolean,
) extends MigrateScenarioData {
  override val currentVersion: Int = 3
}*/
