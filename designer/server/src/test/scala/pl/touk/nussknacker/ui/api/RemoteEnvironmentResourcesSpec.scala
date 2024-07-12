package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, Inside}
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.restmodel.scenariodetails.{
  ScenarioParameters,
  ScenarioWithDetails,
  ScenarioWithDetailsForMigrations
}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.TestFactory.withPermissions
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}
import pl.touk.nussknacker.ui.process.migrate.{
  MissingScenarioGraphError,
  RemoteEnvironment,
  RemoteEnvironmentCommunicationError,
  TestMigrationResult
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.{Difference, NodeNotPresentInCurrent, NodeNotPresentInOther}

import scala.concurrent.{ExecutionContext, Future}

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

  private val processName: ProcessName = ProcessTestData.validProcess.name

  it should "fail when scenario does not exist" in {
    val remoteEnvironment = new MockRemoteEnvironment
    val route = withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer
      ),
      Permission.Read,
      Permission.Write
    )

    Get(s"/remoteEnvironment/$processName/2/compare/1") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No scenario fooProcess found")
    }

    Post(s"/remoteEnvironment/$processName/2/migrate") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No scenario fooProcess found")
    }

    remoteEnvironment.compareInvocations shouldBe Symbol("empty")
    remoteEnvironment.migrateInvocations shouldBe Symbol("empty")

  }

  it should "invoke migration for found scenario" in {
    val difference = Map("node1" -> NodeNotPresentInCurrent("node1", Filter("node1", Expression.spel("#input == 4"))))
    val remoteEnvironment = new MockRemoteEnvironment(mockDifferences = Map(processName -> difference))

    val route = withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer
      ),
      Permission.Read,
      Permission.Write
    )
    val expectedDisplayable = ProcessTestData.validScenarioGraph

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/compare/1") ~> route ~> check {
        status shouldEqual StatusCodes.OK

        responseAs[Map[String, Difference]] shouldBe difference
      }
      remoteEnvironment.compareInvocations shouldBe List(expectedDisplayable)

      Post(s"/remoteEnvironment/$processName/2/migrate") ~> route ~> check {
        status shouldEqual StatusCodes.OK
      }
      remoteEnvironment.migrateInvocations shouldBe List(expectedDisplayable)
    }
  }

  it should "compare environments" in {

    import pl.touk.nussknacker.engine.spel.SpelExtension._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter("a", "".spel))

    val route = withPermissions(
      new RemoteEnvironmentResources(
        new MockRemoteEnvironment(mockDifferences =
          Map(
            processId1 -> Map("n1" -> difference),
            processId2 -> Map()
          )
        ),
        processService,
        processAuthorizer
      ),
      Permission.Read
    )

    saveCanonicalProcess(ProcessTestData.validProcessWithName(processId1)) {
      saveCanonicalProcess(ProcessTestData.validProcessWithName(processId2)) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(ProcessDifference(processId1, presentOnOther = true, Map("n1" -> difference)))
          )
        }
      }
    }

  }

  it should "not fail in comparing environments if process does not exist in the other one" in {
    import pl.touk.nussknacker.engine.spel.SpelExtension._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter("a", "".spel))

    val route = withPermissions(
      new RemoteEnvironmentResources(
        new MockRemoteEnvironment(mockDifferences =
          Map(
            processId1 -> Map("n1" -> difference)
          )
        ),
        processService,
        processAuthorizer
      ),
      Permission.Read
    )

    saveCanonicalProcess(ProcessTestData.validProcessWithName(processId1)) {
      saveCanonicalProcess(ProcessTestData.validProcessWithName(processId2)) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(
              ProcessDifference(processId1, presentOnOther = true, Map("n1" -> difference)),
              ProcessDifference(processId2, presentOnOther = false, Map())
            )
          )
        }
      }
    }
  }

  class MockRemoteEnvironment(
      testMigrationResults: List[TestMigrationResult] = List(),
      val mockDifferences: Map[ProcessName, Map[String, ScenarioGraphComparator.Difference]] = Map()
  ) extends RemoteEnvironment {

    var migrateInvocations = List[ScenarioGraph]()
    var compareInvocations = List[ScenarioGraph]()

    override def compare(
        localScenarioGraph: ScenarioGraph,
        remoteProcessName: ProcessName,
        remoteProcessVersion: Option[VersionId]
    )(
        implicit ec: ExecutionContext
    ): Future[Either[NuDesignerError, Map[String, ScenarioGraphComparator.Difference]]] = {
      compareInvocations = localScenarioGraph :: compareInvocations
      Future.successful(
        mockDifferences
          .get(remoteProcessName)
          .fold[Either[NuDesignerError, Map[String, ScenarioGraphComparator.Difference]]](
            Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, ""))
          )(diffs => Right(diffs))
      )
    }

    override def processVersions(processName: ProcessName)(
        implicit ec: ExecutionContext
    ): Future[List[ScenarioVersion]] = Future.successful(List())

    override def testMigration(
        processToInclude: ScenarioWithDetailsForMigrations => Boolean,
        batchingExecutionContext: ExecutionContext
    )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[NuDesignerError, List[TestMigrationResult]]] = {
      Future.successful(Right(testMigrationResults))
    }

    override def migrate(
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioGraph: ScenarioGraph,
        processName: ProcessName,
        isFragment: Boolean
    )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {
      val localScenarioGraph = scenarioGraph
      migrateInvocations = localScenarioGraph :: migrateInvocations
      Future.successful(Right(()))
    }

  }

}
