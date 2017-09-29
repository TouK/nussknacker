package pl.touk.nussknacker.ui.process.migrate

import argonaut.PrettyParams
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.graph.node.Processor
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.helpers.TestFactory.newProcessActivityRepository
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, WithDbTesting}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import shapeless.Typeable._
import shapeless.syntax.typeable.typeableOps

import scala.concurrent.ExecutionContext.Implicits._

class ProcessModelMigratorSpec extends FlatSpec with BeforeAndAfterEach with ScalaFutures with Matchers with WithDbTesting {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  private val marshaller = UiProcessMarshaller()

  private val activityRepository = newProcessActivityRepository(db)

  private def migrator(migrations: Int*) =
    new ProcessModelMigrator(TestFactory.newProcessRepository(db, Some(migrations.max)), activityRepository, 
      Map(ProcessingType.Streaming -> new TestMigrations(migrations: _*)))

  val processId = "fooProcess"


  private implicit val user = LoggedUser("test1", "", List(Permission.Admin), List())

  it should "migrate processes to new versions when not migrated" in {

    val savedAndMigrated: ProcessDetails = migrateByVersions(None, 1, 2)

    savedAndMigrated.modelVersion shouldBe Some(2)

    extractParallelism(savedAndMigrated) shouldBe 11

    val processor = extractProcessor(savedAndMigrated)
    activityRepository.findActivity(processId)
      .futureValue.comments.map(_.content) shouldBe List("Migrations applied: testMigration1, testMigration2")
    processor shouldBe ServiceRef(ProcessTestData.otherExistingServiceId, List())
  }

  it should "migrate processes to new versions only if migrations not applied" in {

    val savedAndMigrated: ProcessDetails = migrateByVersions(Some(1), 1, 2)

    savedAndMigrated.modelVersion shouldBe Some(2)

    extractParallelism(savedAndMigrated) shouldBe 11

    val processor = extractProcessor(savedAndMigrated)
    processor shouldBe ServiceRef(ProcessTestData.existingServiceId, List())
  }

  private def migrateByVersions(startFrom: Option[Int], migrations: Int*) : ProcessDetails = {
    val repository = TestFactory.newProcessRepository(db, startFrom)

    (for {
      a <- repository.saveNewProcess("fooProcess", "cat1",
        GraphProcess(marshaller.toJson(ProcessTestData.validProcess, PrettyParams.nospace)), ProcessingType.Streaming, false)
      _ <- migrator(migrations: _*).migrate
      migrated <- repository.fetchLatestProcessDetailsForProcessId("fooProcess")
    } yield migrated.get).futureValue

  }

  private def extractProcessor(processDetails: ProcessDetails) = {
    val service = for {
      json <- processDetails.json
      node <- json.nodes.find(_.id == "processor")
      processor <- node.cast[Processor]
    } yield processor.service
    service.get
  }

  private def extractParallelism(processDetails: ProcessDetails) = {
    (for {
      json <- processDetails.json
      stream <- json.properties.typeSpecificProperties.cast[StreamMetaData]
      parallelism <- stream.parallelism
    } yield parallelism).get
  }
}
