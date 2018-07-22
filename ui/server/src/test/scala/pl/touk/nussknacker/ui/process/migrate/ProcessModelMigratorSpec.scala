package pl.touk.nussknacker.ui.process.migrate

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.node.{Processor, asProcessor}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.helpers.TestPermissions
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import shapeless.Typeable._
import shapeless.syntax.typeable.typeableOps

class ProcessModelMigratorSpec extends FlatSpec with BeforeAndAfterEach with ScalaFutures with Matchers with TestPermissions{

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  private def migrator(migrations: Int*) =
    new ProcessModelMigrator(Map(ProcessingType.Streaming -> new TestMigrations(migrations: _*)))

  val processId = "fooProcess"


  private implicit val user = LoggedUser("test1", testPermissionAdmin)

  it should "migrate processes to new versions when not migrated" in {

    val migrationResult: MigrationResult = migrateByVersions(None, 1, 2)

    extractParallelism(migrationResult) shouldBe 11

    migrationResult.toUpdateAction.comment shouldBe "Migrations applied: testMigration1, testMigration2"

    val processor = extractProcessor(migrationResult)
    processor shouldBe ServiceRef(ProcessTestData.otherExistingServiceId, List())
  }

  it should "migrate processes to new versions only if migrations not applied" in {

    val migrationResult: MigrationResult = migrateByVersions(Some(1), 1, 2)

    extractParallelism(migrationResult) shouldBe 11

    val processor = extractProcessor(migrationResult)
    migrationResult.toUpdateAction.comment shouldBe "Migrations applied: testMigration2"
    processor shouldBe ServiceRef(ProcessTestData.existingServiceId, List())
  }

  private def migrateByVersions(startFrom: Option[Int], migrations: Int*) : MigrationResult =
    migrator(migrations: _*).migrateProcess(
      ProcessTestData.toDetails(ProcessTestData.validDisplayableProcess.toDisplayable).copy(modelVersion = startFrom)).get


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
