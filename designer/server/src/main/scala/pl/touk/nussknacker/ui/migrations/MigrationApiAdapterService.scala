package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  CurrentScenarioMigrateRequest,
  LowestScenarioMigrateRequest,
  MigrateScenarioRequest,
  MigrateScenarioRequestV1_14,
  MigrateScenarioRequestV1_15
}
import pl.touk.nussknacker.ui.process.migrate.NuVersionDeserializationError

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

class MigrationApiAdapterService {
  private class MigrationApiAdapter[S, T](val map: S => T, val comap: T => S)

  private object Adapters {

    val adapterV14ToV15: MigrationApiAdapter[MigrateScenarioRequestV1_14, MigrateScenarioRequestV1_15] =
      new MigrationApiAdapter[MigrateScenarioRequestV1_14, MigrateScenarioRequestV1_15](
        v14 =>
          MigrateScenarioRequestV1_15(
            sourceEnvironmentId = v14.sourceEnvironmentId,
            processingMode = v14.processingMode,
            engineSetupName = v14.engineSetupName,
            processCategory = v14.processCategory,
            scenarioGraph = v14.scenarioGraph,
            processName = v14.processName,
            isFragment = v14.isFragment
          ),
        v15 =>
          MigrateScenarioRequestV1_14(
            sourceEnvironmentId = v15.sourceEnvironmentId,
            processingMode = v15.processingMode,
            engineSetupName = v15.engineSetupName,
            processCategory = v15.processCategory,
            scenarioGraph = v15.scenarioGraph,
            processName = v15.processName,
            isFragment = v15.isFragment
          )
      )

  }

  // @tailrec <- later
  def adaptToHighestVersion(migrateScenarioRequest: MigrateScenarioRequest): CurrentScenarioMigrateRequest =
    migrateScenarioRequest match {
      case v14 @ MigrateScenarioRequestV1_14(_, _, _, _, _, _, _) => Adapters.adapterV14ToV15.map(v14)
      case currentVersion: CurrentScenarioMigrateRequest          => currentVersion
    }

  /*
    FIXME: This function should be expanded to take additional argument `versionToAdapt` and adapt
           to determined version. Currently it is very greedy and paranoic beacuse we lower version as much
           as possible and possibly it would need to be lifted up on the other side. So, one should delete
           `adaptToLowestVersion` and create `adaptToLowerVersion(req, versionToAdapt)`.
   */

  // @tailrec - later
  def adaptToLowestVersion(migrateScenarioRequest: MigrateScenarioRequest): LowestScenarioMigrateRequest =
    migrateScenarioRequest match {
      case lowestVersion: LowestScenarioMigrateRequest            => lowestVersion
      case v15 @ MigrateScenarioRequestV1_15(_, _, _, _, _, _, _) => Adapters.adapterV14ToV15.comap(v15)
    }

  def decideMigrationRequestDto(
      migrateScenarioRequest: CurrentScenarioMigrateRequest,
      versionComparisionResult: Int
  ): MigrateScenarioRequest = {
    if (versionComparisionResult <= 0) {
      migrateScenarioRequest
    } else {
      adaptToLowestVersion(migrateScenarioRequest)
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
