package pl.touk.nussknacker.ui.api

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.scalatest.{BeforeAndAfterEach, Inside}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestFactory.withPermissions
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.ProcessesResources
import pl.touk.nussknacker.ui.process.migrate.{
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
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
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
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
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
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
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

  it should "return only remote version IDs that have meaningful differences" in {
    import java.time.Instant
    import pl.touk.nussknacker.engine.spel.SpelExtension._

    val versionWithDiff    = VersionId(1)
    val versionWithoutDiff = VersionId(2)
    val difference = Map("node1" -> NodeNotPresentInCurrent("node1", Filter("node1", "#input == 4".spel)))

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[List[ScenarioVersion]] =
        Future.successful(
          List(
            ScenarioVersion(versionWithDiff, Instant.now(), "user"),
            ScenarioVersion(versionWithoutDiff, Instant.now(), "user"),
          )
        )

      override def compare(
          localScenarioGraph: ScenarioGraph,
          remoteProcessName: ProcessName,
          remoteProcessVersion: Option[VersionId]
      ): Future[Either[NuDesignerError, Map[String, ScenarioGraphComparator.Difference]]] =
        Future.successful(
          Right(if (remoteProcessVersion.contains(versionWithDiff)) difference else Map.empty)
        )
    }

    val route = withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
      ),
      Permission.Read
    )

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/1/versions-with-differences") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val result = responseAs[ProcessesResources.VersionsWithDifferences]
        result.versionIds.map(_.value) should contain(versionWithDiff.value)
        result.versionIds.map(_.value) should not contain versionWithoutDiff.value
        result.hasMore shouldBe false
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
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
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

    override def environmentId: String = "test-remote-env"

    var migrateInvocations = List[ScenarioGraph]()
    var compareInvocations = List[ScenarioGraph]()

    override def compare(
        localScenarioGraph: ScenarioGraph,
        remoteProcessName: ProcessName,
        remoteProcessVersion: Option[VersionId]
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

    override def processVersions(processName: ProcessName): Future[List[ScenarioVersion]] = Future.successful(List())

    override def testMigration(
        processToInclude: ScenarioWithDetailsForMigrations => Boolean,
        batchingExecutionContext: ExecutionContext
    )(
        implicit loggedUser: LoggedUser
    ): Future[Either[NuDesignerError, List[TestMigrationResult]]] = {
      Future.successful(Right(testMigrationResults))
    }

    override def migrate(
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioLabels: List[String],
        scenarioGraph: ScenarioGraph,
        localScenarioVersionId: VersionId,
        processName: ProcessName,
        isFragment: Boolean
    )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {
      val localScenarioGraph = scenarioGraph
      migrateInvocations = localScenarioGraph :: migrateInvocations
      Future.successful(Right(()))
    }

  }

}
