package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  MigrateScenarioRequestDto,
  MigrateScenarioRequestDtoV1,
  MigrateScenarioRequestDtoV2
}
import pl.touk.nussknacker.ui.process.migrate.NuVersionDeserializationError

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

sealed trait MigrationApiAdapter {
  def map: MigrateScenarioRequest => MigrateScenarioRequest
  def comap: MigrateScenarioRequest => MigrateScenarioRequest
}

object Adapters {

  case object AdapterV1ToV2 extends MigrationApiAdapter {

    override def map: MigrateScenarioRequest => MigrateScenarioRequest = {
      case v1: MigrateScenarioRequestV1 =>
        MigrateScenarioRequestV2(
          version = v1.version,
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

    override def comap: MigrateScenarioRequest => MigrateScenarioRequest = {
      case v2: MigrateScenarioRequestV2 =>
        MigrateScenarioRequestV1(
          version = v2.version,
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

  val adapters: Map[Int, MigrationApiAdapter] = Map(1 -> Adapters.AdapterV1ToV2)
}

class MigrationApiAdapterService {

  def getCurrentApiVersion: Int = Adapters.adapters.keySet.size

  def adaptDown(migrateScenarioRequest: MigrateScenarioRequest, noOfVersions: Int): MigrateScenarioRequest =
    adaptN(migrateScenarioRequest, noOfVersions)(_ - 1)

  def adaptUp(migrateScenarioRequest: MigrateScenarioRequest, noOfVersions: Int): MigrateScenarioRequest =
    adaptN(migrateScenarioRequest, noOfVersions)(_ + 1)

  @tailrec
  private def adaptN(migrateScenarioRequest: MigrateScenarioRequest, noOfVersions: Int)(
      modifier: Int => Int
  ): MigrateScenarioRequest = {
    val currentVersion = migrateScenarioRequest.currentVersion()

    noOfVersions match {
      case 0 => migrateScenarioRequest
      case _ =>
        val adapter = Adapters.adapters(currentVersion + 1)
        adaptN(adapter.map(migrateScenarioRequest), modifier(noOfVersions))(modifier)
    }
  }

}
