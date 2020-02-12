package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import cats.syntax.semigroup._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.processdetails.ProcessVersion
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationErrorType, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironment, RemoteEnvironmentCommunicationError, TestMigrationResult}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.nussknacker.ui.util.ProcessComparator.{Difference, NodeNotPresentInCurrent, NodeNotPresentInOther}

import scala.concurrent.{ExecutionContext, Future}

class RemoteEnvironmentResourcesSpec extends FlatSpec with ScalatestRouteTest with PatientScalaFutures with Matchers with FailFastCirceSupport
  with BeforeAndAfterEach with Inside with EspItTest {
  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val processId: String = ProcessTestData.validProcess.id
  private val processName: ProcessName = ProcessName(processId)

  val readWritePermissions: CategorizedPermission = testPermissionRead |+| testPermissionWrite
  it should "fail when process does not exist" in {
    val remoteEnvironment = new MockRemoteEnvironment
    val route = withPermissions(new RemoteEnvironmentResources(remoteEnvironment, processRepository, processAuthorizer),readWritePermissions)

    Get(s"/remoteEnvironment/$processId/2/compare/1") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No process fooProcess found")
    }

    Post(s"/remoteEnvironment/$processId/2/migrate") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No process fooProcess found")
    }

    remoteEnvironment.compareInvocations shouldBe 'empty
    remoteEnvironment.migrateInvocations shouldBe 'empty

  }

  it should "invoke migration for found process" in {
    val difference = Map("node1" -> NodeNotPresentInCurrent("node1", Filter("node1", Expression("spel", "#input == 4"))))
    val remoteEnvironment = new MockRemoteEnvironment(mockDifferences = Map(processId -> difference))

    val route = withPermissions(new RemoteEnvironmentResources(remoteEnvironment, processRepository, processAuthorizer), readWritePermissions)

    saveProcess(processName, ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processId/2/compare/1") ~> route ~> check {
        status shouldEqual StatusCodes.OK

        responseAs[Map[String, Difference]] shouldBe difference
      }
      remoteEnvironment.compareInvocations shouldBe List(ProcessTestData.validDisplayableProcess.toDisplayable)


      Post(s"/remoteEnvironment/$processId/2/migrate") ~> route ~> check {
        status shouldEqual StatusCodes.OK
      }
      remoteEnvironment.migrateInvocations shouldBe List(ProcessTestData.validDisplayableProcess.toDisplayable)

    }
  }

  it should "return 500 on failed test migration" in {
    val error = ValidationResults.NodeValidationError("foo", "bar", "baz", None, NodeValidationErrorType.SaveAllowed)
    val validationResult = ValidationResult.success.copy(errors = ValidationResult.success.errors.copy(invalidNodes = Map("a" -> List(error))))

    val process = withDecodedTypes(ProcessTestData.validDisplayableProcess)
    val results = List(
      TestMigrationResult(process.copy(id = "failingProcess"), validationResult, true),
      TestMigrationResult(process.copy(id = "notFailing"), ValidationResult.success, false)
    )
    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(results), processRepository, processAuthorizer), testPermissionRead)

    Get(s"/remoteEnvironment/testAutomaticMigration") ~> route ~> check {
      status shouldEqual StatusCodes.InternalServerError

      responseAs[TestMigrationSummary] shouldBe TestMigrationSummary("Migration failed, following processes have new errors: failingProcess", results)
    }
  }

  it should "return result on test migration" in {

    val process = withDecodedTypes(ProcessTestData.validDisplayableProcess)
    val results = List(
      TestMigrationResult(process, ValidationResult.success, true),
      TestMigrationResult(process.copy(id = "notFailing"), ValidationResult.success, false)
    )

    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(results), processRepository, processAuthorizer), testPermissionRead)

    Get(s"/remoteEnvironment/testAutomaticMigration") ~> route ~> check {
      status shouldEqual StatusCodes.OK

      responseAs[TestMigrationSummary] shouldBe TestMigrationSummary("Migrations successful", results)
    }
  }

  it should "compare environments" in {

    import pl.touk.nussknacker.engine.spel.Implicits._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter("a", ""))


    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(mockDifferences = Map(
      processId1.value -> Map("n1" -> difference),
      processId2.value -> Map()

    )),
      processRepository, processAuthorizer), testPermissionRead)

    saveProcess(processId1, ProcessTestData.validProcessWithId(processId1.value)) {
      saveProcess(processId2, ProcessTestData.validProcessWithId(processId2.value)) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(ProcessDifference(processId1.value, true, Map("n1" -> difference))))
        }
      }
    }

  }

  it should "not fail in comparing environments if process does not exist in the other one" in {
    import pl.touk.nussknacker.engine.spel.Implicits._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter("a", ""))


    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(mockDifferences = Map(
      processId1.value -> Map("n1" -> difference)
    )),
      processRepository, processAuthorizer), readWritePermissions)

    saveProcess(processId1, ProcessTestData.validProcessWithId(processId1.value)) {
      saveProcess(processId2, ProcessTestData.validProcessWithId(processId2.value)) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(ProcessDifference(processId1.value, true, Map("n1" -> difference)), ProcessDifference(processId2.value, false, Map())))
        }
      }
    }
  }

  //we replace Types with Unkown because this is how types from RemoteEnvironment are decoded (to avoid classloading issues...)
  private def withDecodedTypes(process: ValidatedDisplayableProcess) = {
    val validationResult = process.validationResult
    process.copy(validationResult = validationResult.copy(variableTypes = validationResult
        .variableTypes.mapValues(_.mapValues(_ => Unknown))))
  }

  class MockRemoteEnvironment(testMigrationResults: List[TestMigrationResult] = List(),
                              val mockDifferences : Map[String, Map[String, ProcessComparator.Difference]] = Map()) extends RemoteEnvironment {

    var migrateInvocations = List[DisplayableProcess]()
    var compareInvocations = List[DisplayableProcess]()

    override def migrate(localProcess: DisplayableProcess, category: String)(implicit ec: ExecutionContext, user: LoggedUser) = {
      migrateInvocations = localProcess :: migrateInvocations
      Future.successful(Right(()))
    }

    override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long],
                  businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, ProcessComparator.Difference]]]= {
      compareInvocations = localProcess :: compareInvocations
      Future.successful(mockDifferences.get(localProcess.id).fold[Either[EspError, Map[String, ProcessComparator.Difference]]](Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, "")))
        (diffs => Right(diffs)))
    }

    override def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ProcessVersion]] = Future.successful(List())

    override def testMigration(implicit ec: ExecutionContext): Future[Either[EspError, List[TestMigrationResult]]] = {
      Future.successful(Right(testMigrationResults))
    }

    override def targetEnvironmentId = "abcd"
  }


}
