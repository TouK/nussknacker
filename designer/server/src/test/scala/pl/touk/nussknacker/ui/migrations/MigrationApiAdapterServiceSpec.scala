package pl.touk.nussknacker.ui.migrations

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ProcessingMode.UnboundedStream
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  MigrateScenarioRequestV1_14,
  MigrateScenarioRequestV1_15
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.migrate.NuVersionDeserializationError

class MigrationApiAdapterServiceSpec extends AnyFlatSpec with Matchers {

  private val migrationApiAdapterService: MigrationApiAdapterService = new MigrationApiAdapterService()

  it should "adapt lower version of request DTO into highest version" in {
    val adaptedDTO  = migrationApiAdapterService.adaptToLowestVersion(migrateScenarioRequestV1_15)
    val expectedDTO = migrateScenarioRequestV1_14

    adaptedDTO shouldEqual expectedDTO
  }

  it should "adapt higher version of request DTO into lowest version" in {
    val adaptedDTO  = migrationApiAdapterService.adaptToHighestVersion(migrateScenarioRequestV1_14)
    val expectedDTO = migrateScenarioRequestV1_15

    adaptedDTO shouldEqual expectedDTO
  }

  it should "return 0 when comparing the same nu versions" in {
    val actualVersionComparsionResult = migrationApiAdapterService.compareNuVersions(nuVersion1, nuVersion1)
    val expectedComparsionResult: Either[NuDesignerError, Int] = Right(0)

    actualVersionComparsionResult shouldEqual expectedComparsionResult
  }

  it should "return negative versions difference when local Nu version is smaller than remote Nu version at the major level" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(localNuVersion = nuVersion1, remoteNuVersion = nuVersion2)

    actualVersionComparsionResult shouldEqual Right(-2)
  }

  it should "return negative versions difference when local Nu version is smaller than remote Nu version at the minor level" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(localNuVersion = nuVersion3, remoteNuVersion = nuVersion4)

    actualVersionComparsionResult shouldEqual Right(-1)
  }

  it should "return negative versions difference when local Nu version is smaller than remote Nu version at the path level" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(localNuVersion = nuVersion5, remoteNuVersion = nuVersion6)

    actualVersionComparsionResult shouldEqual Right(-7)
  }

  it should "return positive versions difference when local Nu version is greater than remote Nu version at the major level" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(localNuVersion = nuVersion2, remoteNuVersion = nuVersion1)

    actualVersionComparsionResult shouldEqual Right(2)
  }

  it should "return positive versions difference when local Nu version is greater than remote Nu version at the minor level" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(localNuVersion = nuVersion4, remoteNuVersion = nuVersion3)

    actualVersionComparsionResult shouldEqual Right(1)
  }

  it should "return positive versions difference when local Nu version is greater than remote Nu version at the path level" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(localNuVersion = nuVersion6, remoteNuVersion = nuVersion5)

    actualVersionComparsionResult shouldEqual Right(7)
  }

  it should "fail when incorrect local Nu version was provided" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(incorrectNuVersion, nuVersion3)

    actualVersionComparsionResult shouldBe Left(
      NuVersionDeserializationError(incorrectNuVersion)
    )
  }

  it should "fail when incorrect remote Nu version was provided" in {
    val actualVersionComparsionResult =
      migrationApiAdapterService.compareNuVersions(nuVersion3, incorrectNuVersion)

    actualVersionComparsionResult shouldBe Left(
      NuVersionDeserializationError(incorrectNuVersion)
    )
  }

  private lazy val nuVersion1 = "1.14.0"
  private lazy val nuVersion2 = "3.0.0"

  private lazy val nuVersion3 = "1.14.0-SNAPSHOT"
  private lazy val nuVersion4 = "1.15.2"

  private lazy val nuVersion5 = "1.14.0"
  private lazy val nuVersion6 = "1.14.7"

  private lazy val incorrectNuVersion = "114.2-SNAPSHOT"

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData("test", Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private val migrateScenarioRequestV1_14: MigrateScenarioRequestV1_14 =
    MigrateScenarioRequestV1_14(
      sourceEnvironmentId = "DEV",
      processingMode = UnboundedStream,
      engineSetupName = EngineSetupName("Flink"),
      processCategory = "Category1",
      scenarioGraph = exampleGraph,
      processName = ProcessName("test"),
      isFragment = false
    )

  private val migrateScenarioRequestV1_15: MigrateScenarioRequestV1_15 =
    MigrateScenarioRequestV1_15(
      sourceEnvironmentId = "DEV",
      processingMode = UnboundedStream,
      engineSetupName = EngineSetupName("Flink"),
      processCategory = "Category1",
      scenarioGraph = exampleGraph,
      processName = ProcessName("test"),
      isFragment = false
    )

}
