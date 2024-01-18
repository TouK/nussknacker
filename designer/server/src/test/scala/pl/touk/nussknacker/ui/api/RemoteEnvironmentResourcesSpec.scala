package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import cats.syntax.semigroup._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{BeforeAndAfterEach, Inside}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.restmodel.scenariodetails
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, ProcessTestData}
import pl.touk.nussknacker.ui.process.migrate.{
  RemoteEnvironment,
  RemoteEnvironmentCommunicationError,
  TestMigrationResult
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.nussknacker.ui.util.ProcessComparator.{Difference, NodeNotPresentInCurrent, NodeNotPresentInOther}

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess

import scala.concurrent.duration.FiniteDuration

class RemoteEnvironmentResourcesSpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with PatientScalaFutures
    with Matchers
    with FailFastCirceSupport
    with BeforeAndAfterEach
    with Inside
    with NuResourcesTest {
  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val processId: String        = ProcessTestData.validProcess.id
  private val processName: ProcessName = ProcessName(processId)

  val readWritePermissions: CategorizedPermission = testPermissionRead |+| testPermissionWrite

  it should "fail when scenario does not exist" in {
    val remoteEnvironment = new MockRemoteEnvironment
    val route = withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer
      ),
      readWritePermissions
    )

    Get(s"/remoteEnvironment/$processId/2/compare/1") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No scenario fooProcess found")
    }

    Post(s"/remoteEnvironment/$processId/2/migrate") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No scenario fooProcess found")
    }

    remoteEnvironment.compareInvocations shouldBe Symbol("empty")
    remoteEnvironment.migrateInvocations shouldBe Symbol("empty")

  }

  it should "invoke migration for found scenario" in {
    val category   = Category1
    val difference = Map("node1" -> NodeNotPresentInCurrent("node1", Filter("node1", Expression.spel("#input == 4"))))
    val remoteEnvironment = new MockRemoteEnvironment(mockDifferences = Map(processId -> difference))

    val route = withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer
      ),
      readWritePermissions
    )
    val expectedDisplayable = ProcessTestData.validDisplayableProcess.toDisplayable.copy(category = category)

    saveProcess(processName, ProcessTestData.validProcess, category) {
      Get(s"/remoteEnvironment/$processId/2/compare/1") ~> route ~> check {
        status shouldEqual StatusCodes.OK

        responseAs[Map[String, Difference]] shouldBe difference
      }
      remoteEnvironment.compareInvocations shouldBe List(expectedDisplayable)

      Post(s"/remoteEnvironment/$processId/2/migrate") ~> route ~> check {
        status shouldEqual StatusCodes.OK
      }
      remoteEnvironment.migrateInvocations shouldBe List(expectedDisplayable)
    }
  }

  it should "compare environments" in {

    import pl.touk.nussknacker.engine.spel.Implicits._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter("a", ""))

    val route = withPermissions(
      new RemoteEnvironmentResources(
        new MockRemoteEnvironment(mockDifferences =
          Map(
            processId1.value -> Map("n1" -> difference),
            processId2.value -> Map()
          )
        ),
        processService,
        processAuthorizer
      ),
      testPermissionRead
    )

    saveProcess(processId1, ProcessTestData.validProcessWithId(processId1.value), Category1) {
      saveProcess(processId2, ProcessTestData.validProcessWithId(processId2.value), Category1) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(ProcessDifference(processId1.value, presentOnOther = true, Map("n1" -> difference)))
          )
        }
      }
    }

  }

  it should "not fail in comparing environments if process does not exist in the other one" in {
    import pl.touk.nussknacker.engine.spel.Implicits._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter("a", ""))

    val route = withPermissions(
      new RemoteEnvironmentResources(
        new MockRemoteEnvironment(mockDifferences =
          Map(
            processId1.value -> Map("n1" -> difference)
          )
        ),
        processService,
        processAuthorizer
      ),
      readWritePermissions
    )

    saveProcess(processId1, ProcessTestData.validProcessWithId(processId1.value), Category1) {
      saveProcess(processId2, ProcessTestData.validProcessWithId(processId2.value), Category1) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(
              ProcessDifference(processId1.value, presentOnOther = true, Map("n1" -> difference)),
              ProcessDifference(processId2.value, presentOnOther = false, Map())
            )
          )
        }
      }
    }
  }

  class MockRemoteEnvironment(
      testMigrationResults: List[TestMigrationResult] = List(),
      val mockDifferences: Map[String, Map[String, ProcessComparator.Difference]] = Map()
  ) extends RemoteEnvironment {

    var migrateInvocations = List[DisplayableProcess]()
    var compareInvocations = List[DisplayableProcess]()

    override def migrate(
        localProcess: DisplayableProcess,
        category: String
    )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[NuDesignerError, Unit]] = {
      migrateInvocations = localProcess :: migrateInvocations
      Future.successful(Right(()))
    }

    override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[VersionId])(
        implicit ec: ExecutionContext
    ): Future[Either[NuDesignerError, Map[String, ProcessComparator.Difference]]] = {
      compareInvocations = localProcess :: compareInvocations
      Future.successful(
        mockDifferences
          .get(localProcess.id)
          .fold[Either[NuDesignerError, Map[String, ProcessComparator.Difference]]](
            Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, ""))
          )(diffs => Right(diffs))
      )
    }

    override def processVersions(processName: ProcessName)(
        implicit ec: ExecutionContext
    ): Future[List[ScenarioVersion]] = Future.successful(List())

    override def testMigration(
        processToInclude: ScenarioWithDetails => Boolean,
        batchingExecutionContext: ExecutionContext
    )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[NuDesignerError, List[TestMigrationResult]]] = {
      Future.successful(Right(testMigrationResults))
    }

  }

}
