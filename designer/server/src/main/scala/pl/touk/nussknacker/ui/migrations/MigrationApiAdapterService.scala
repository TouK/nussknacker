package pl.touk.nussknacker.ui.migrations

import scala.annotation.tailrec

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

trait ApiAdapter[D] {
  def liftVersion: D => D
  def downgradeVersion: D => D
}

trait VersionedDto {
  def currentVersion(): Int
}

trait MigrationApiAdapterServiceInterface[D <: VersionedDto] {
  def getAdapters: Map[Int, ApiAdapter[D]]
  def getCurrentApiVersion: Int = getAdapters.keySet.size + 1

  def adaptDown(migrateScenarioRequest: D, noOfVersions: Int): D =
    adaptN(migrateScenarioRequest, -noOfVersions)

  def adaptUp(migrateScenarioRequest: D, noOfVersions: Int): D =
    adaptN(migrateScenarioRequest, noOfVersions)

  @tailrec
  private def adaptN(migrateScenarioRequest: D, noOfVersions: Int): D = {
    val currentVersion = migrateScenarioRequest.currentVersion()
    val adapters       = getAdapters

    noOfVersions match {
      case 0 => migrateScenarioRequest
      case n if n > 0 =>
        val adapter = adapters(currentVersion - 1)
        adaptN(adapter.liftVersion(migrateScenarioRequest), noOfVersions - 1)
      case n if n < 0 =>
        val adapter = adapters(currentVersion - 1)
        adaptN(adapter.downgradeVersion(migrateScenarioRequest), noOfVersions + 1)
    }
  }

}

class MigrationApiAdapterService extends MigrationApiAdapterServiceInterface[MigrateScenarioRequest] {
  override def getAdapters: Map[Int, ApiAdapter[MigrateScenarioRequest]] = MigrationApiAdapters.adapters
}
