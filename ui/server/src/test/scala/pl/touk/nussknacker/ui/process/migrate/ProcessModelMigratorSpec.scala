package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.node.asProcessor
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestPermissions, TestProcessingTypes}
import shapeless.Typeable._
import shapeless.syntax.typeable.typeableOps

class ProcessModelMigratorSpec extends FlatSpec with BeforeAndAfterEach with PatientScalaFutures with Matchers with TestPermissions{

  private def migrator(migrations: Int*) =
    new ProcessModelMigrator(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new TestMigrations(migrations: _*)))

  val processId = "fooProcess"


  private implicit val user = TestFactory.adminUser("test1")

  it should "migrate processes to new versions when not migrated" in {

    val migrationResult: MigrationResult = migrateByVersions(None, 1, 2)

    extractParallelism(migrationResult) shouldBe 11

    migrationResult.toUpdateAction(ProcessId(1L)).comment shouldBe "Migrations applied: testMigration1, testMigration2"

    val processor = extractProcessor(migrationResult)
    processor shouldBe ServiceRef(ProcessTestData.otherExistingServiceId, List())
  }

  it should "migration should not return empty migration result" in {

    val migrationResultOpt: Option[MigrationResult] = migrateByVersionsOpt(Some(1), 1)

    migrationResultOpt shouldBe empty

  }

  it should "migrate processes to new versions only if migrations not applied" in {

    val migrationResult: MigrationResult = migrateByVersions(Some(1), 1, 2)

    extractParallelism(migrationResult) shouldBe 11

    val processor = extractProcessor(migrationResult)
    migrationResult.toUpdateAction(ProcessId(1L)).comment shouldBe "Migrations applied: testMigration2"
    processor shouldBe ServiceRef(ProcessTestData.existingServiceId, List())
  }

  private def migrateByVersions(startFrom: Option[Int], migrations: Int*) : MigrationResult =
    migrateByVersionsOpt(startFrom, migrations: _*).get

  private def migrateByVersionsOpt(startFrom: Option[Int], migrations: Int*) : Option[MigrationResult] =
    migrator(migrations: _*).migrateProcess(
      ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess.toDisplayable).copy(modelVersion = startFrom), skipEmptyMigrations = true)

  private def extractProcessor(migrationResult: MigrationResult) = {
    val service = for {
      node <- migrationResult.process.nodes.find(_.id == "processor")
      flatNode <- node.cast[FlatNode]
      processor <- asProcessor(flatNode.data)
    } yield processor.service
    service.get
  }

  private def extractParallelism(migrationResult: MigrationResult) = {
    (for {
      stream <- migrationResult.process.metaData.typeSpecificData.cast[StreamMetaData]
      parallelism <- stream.parallelism
    } yield parallelism).get
  }
}
