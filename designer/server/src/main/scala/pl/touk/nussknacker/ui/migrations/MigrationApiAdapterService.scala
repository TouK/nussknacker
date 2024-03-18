package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.MigrationApiEndpoints.Dtos.{
  MigrateScenarioRequest,
  MigrateScenarioRequestV1,
  MigrateScenarioRequestV2
}
import pl.touk.nussknacker.ui.process.migrate.NuVersionDeserializationError

import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

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

  def decideMigrationRequestDto(
      migrateScenarioRequestV2: MigrateScenarioRequestV2,
      versionComparisionResult: Int
  ): MigrateScenarioRequest = {
    if (versionComparisionResult <= 0) {
      migrateScenarioRequestV2
    } else {
      adaptToLowerVersion(migrateScenarioRequestV2)
    }
  }

  // compare(local, remote) <= 0 <=> local <= remote
  def compareNuVersions(localNuVersion: String, remoteNuVersion: String): Either[NuDesignerError, Int] = {
    val versionPattern = """(\d+)\.(\d+)\.(\d+).*""".r

    val localResult: Try[Regex.Match]  = Try(versionPattern.findFirstMatchIn(localNuVersion).get)
    val remoteResult: Try[Regex.Match] = Try(versionPattern.findFirstMatchIn(remoteNuVersion).get)

    localResult match {
      case Success(localVersionPatternMatch) =>
        remoteResult match {
          case Success(remoteVersionPatternMatch) =>
            val localMajorVersion = localVersionPatternMatch.group(1).toInt
            val localMinorVersion = localVersionPatternMatch.group(2).toInt
            val localPatchVersion = localVersionPatternMatch.group(3).toInt

            val remoteMajorVersion = remoteVersionPatternMatch.group(1).toInt
            val remoteMinorVersion = remoteVersionPatternMatch.group(2).toInt
            val remotePatchVersion = remoteVersionPatternMatch.group(3).toInt

            val res = if (remoteMajorVersion != localMajorVersion) {
              localMajorVersion - remoteMajorVersion
            } else if (remoteMinorVersion != localMinorVersion) {
              localMinorVersion - remoteMinorVersion
            } else {
              localPatchVersion - remotePatchVersion
            }

            Right(res)
          case Failure(_) => Left(NuVersionDeserializationError(remoteNuVersion))
        }
      case Failure(_) => Left(NuVersionDeserializationError(localNuVersion))
    }
  }

}
