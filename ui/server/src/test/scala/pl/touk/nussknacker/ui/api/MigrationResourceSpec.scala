package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.migrate.ProcessMigrator
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.ProcessComparator.{Difference, NodeNotPresentInCurrent}
import pl.touk.nussknacker.ui.validation.ValidationResults.ValidationResult

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import argonaut.ArgonautShapeless._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.ui.util.ProcessComparator

class MigrationResourceSpec extends FlatSpec with ScalatestRouteTest with ScalaFutures with Matchers
  with BeforeAndAfterEach with Inside with EspItTest {


  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  private val processId: String = SampleProcess.process.id

  val validDisplayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(ProcessTestData.validProcess), ProcessingType.Streaming)
    .copy(validationResult = Some(ValidationResult.success))

  it should "fail when process does not exist" in {
    val migrator = new MockMigrator
    val route = withPermissions(new MigrationResources(migrator, processRepository), Permission.Deploy)

    Get(s"/migration/compare/$processId") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No process sampleProcess found")
    }

    Post(s"/migration/migrate/$processId") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No process sampleProcess found")
    }

    migrator.compareInvocations shouldBe 'empty
    migrator.migrateInvocations shouldBe 'empty

  }

  it should "invoke migrator for found process" in {
    val migrator = new MockMigrator
    val route = withPermissions(new MigrationResources(migrator, processRepository), Permission.Deploy)
    import pl.touk.http.argonaut.Argonaut62Support._
    implicit val codec = ProcessComparator.codec

    saveProcess(processId, ProcessTestData.validProcess) {
      Get(s"/migration/compare/$processId") ~> route ~> check {
        status shouldEqual StatusCodes.OK

        responseAs[Map[String, Difference]] shouldBe migrator.mockDifference
      }
      migrator.compareInvocations shouldBe List(validDisplayable)


      Post(s"/migration/migrate/$processId") ~> route ~> check {
        status shouldEqual StatusCodes.OK
      }
      migrator.migrateInvocations shouldBe List(validDisplayable)

    }
  }


  class MockMigrator extends ProcessMigrator {

    var migrateInvocations = List[DisplayableProcess]()

    var compareInvocations = List[DisplayableProcess]()

    val mockDifference = Map("node1" -> NodeNotPresentInCurrent("node1", Filter("node1", Expression("spel", "#input == 4"))))

    override def migrate(localProcess: DisplayableProcess)(implicit ec: ExecutionContext, user: LoggedUser) = {
      migrateInvocations = localProcess::migrateInvocations
      Future.successful(Right(()))
    }

    override def compare(localProcess: DisplayableProcess)(implicit ec: ExecutionContext) = {
      compareInvocations = localProcess::compareInvocations
      Future.successful(Right(mockDifference))
    }

    override def targetEnvironmentId = "abcd"
  }


}
