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
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{ScenarioActivities, ScenarioActivity}
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.VersionsWithDifferences
import pl.touk.nussknacker.ui.process.migrate.{
  RemoteEnvironment,
  RemoteEnvironmentCommunicationError,
  RemoteScenarioVersions,
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

    val versionWithDiff    = VersionId(1)
    val versionWithoutDiff = VersionId(2)

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(
          RemoteScenarioVersions(
            List(
              ScenarioVersion(versionWithDiff, Instant.now(), "user"),
              ScenarioVersion(versionWithoutDiff, Instant.now(), "user"),
            ),
            remoteUnavailable = false
          )
        )

      override def scenarioGraphsForVersions(
          pName: ProcessName,
          versionIds: List[VersionId]
      ): Future[Map[VersionId, ScenarioGraph]] =
        Future.successful(
          Map(
            versionWithDiff    -> ProcessTestData.invalidProcess.toScenarioGraph,
            versionWithoutDiff -> ProcessTestData.validScenarioGraph,
          )
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

    // saveCanonicalProcess creates version 1 (an empty skeleton) via POST, then version 2 with the real
    // content via PUT - so the local graph to diff against is fetched at version 2, matching
    // ProcessTestData.validScenarioGraph (used below as the "no differences" remote version).
    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences?pageNumber=0&pageSize=10") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val result = responseAs[VersionsWithDifferences]
        result.versions.map(_.versionId.value) should contain(versionWithDiff.value)
        result.versions.map(_.versionId.value) should not contain versionWithoutDiff.value
        result.hasMore shouldBe false
      }
    }
  }

  it should "conservatively treat a remote version as different when its graph can't be fetched" in {
    import java.time.Instant

    val versionWithUnknownDiff = VersionId(1)

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(
          RemoteScenarioVersions(List(ScenarioVersion(versionWithUnknownDiff, Instant.now(), "user")), remoteUnavailable = false)
        )

      // Simulates a remote environment that doesn't support the bulk graphs endpoint yet
      // (e.g. an older Nussknacker version) - RemoteEnvironment.scenarioGraphsForVersions
      // resolves to an empty map on any such failure instead of failing the Future.
      override def scenarioGraphsForVersions(
          pName: ProcessName,
          versionIds: List[VersionId]
      ): Future[Map[VersionId, ScenarioGraph]] = Future.successful(Map.empty)
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
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences?pageNumber=0&pageSize=10") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val result = responseAs[VersionsWithDifferences]
        result.versions.map(_.versionId.value) should contain(versionWithUnknownDiff.value)
      }
    }
  }

  it should "return activities from the remote environment" in {
    val activity = ScenarioActivity.forScenarioCreated(
      id = java.util.UUID.fromString("80c95497-3b53-4435-b2d9-ae73c5766213"),
      user = "some user",
      date = java.time.Instant.parse("2024-01-17T14:21:17Z"),
      scenarioVersionId = Some(1),
    )

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def activities(pName: ProcessName): Future[List[ScenarioActivity]] =
        Future.successful(List(activity))
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
      Get(s"/remoteEnvironment/$processName/activities") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ScenarioActivities] shouldBe ScenarioActivities(List(activity))
      }
    }
  }

  it should "not expose remote activities and versions to a user without read access to the scenario" in {
    val activity = ScenarioActivity.forScenarioCreated(
      id = java.util.UUID.fromString("80c95497-3b53-4435-b2d9-ae73c5766213"),
      user = "some user",
      date = java.time.Instant.parse("2024-01-17T14:21:17Z"),
      scenarioVersionId = Some(1),
    )

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def activities(pName: ProcessName): Future[List[ScenarioActivity]] =
        Future.successful(List(activity))

      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(
          RemoteScenarioVersions(List(ScenarioVersion(VersionId(1), java.time.Instant.now(), "user")), remoteUnavailable = false)
        )
    }

    // The remote environment is queried with the designer's own service account, so without an explicit
    // check any authenticated user could read a scenario they have no access to - comments included.
    val routeWithoutReadPermission = withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
      )
    )

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/activities") ~> routeWithoutReadPermission ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Get(s"/remoteEnvironment/$processName/versions") ~> routeWithoutReadPermission ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }

  it should "not expose remote versions with differences to a user without read access to the scenario" in {
    val routeWithoutReadPermission = withPermissions(
      new RemoteEnvironmentResources(
        new MockRemoteEnvironment,
        processService,
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
      )
    )

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(
        s"/remoteEnvironment/$processName/2/versions-with-differences?pageNumber=0&pageSize=10"
      ) ~> routeWithoutReadPermission ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }

  it should "report that the remote environment is unavailable instead of an empty list of versions" in {
    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(RemoteScenarioVersions(List.empty, remoteUnavailable = true))
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
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences?pageNumber=0&pageSize=10") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val result = responseAs[VersionsWithDifferences]
        result.versions shouldBe empty
        result.hasMore shouldBe false
        result.remoteUnavailable shouldBe true
      }
      Get(s"/remoteEnvironment/$processName/versions") ~> route ~> check {
        status shouldEqual StatusCodes.BadGateway
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

    override def processVersions(processName: ProcessName): Future[RemoteScenarioVersions] =
      Future.successful(RemoteScenarioVersions(List.empty, remoteUnavailable = false))

    override def scenarioGraphsForVersions(
        processName: ProcessName,
        versionIds: List[VersionId]
    ): Future[Map[VersionId, ScenarioGraph]] = Future.successful(Map.empty)

    override def activities(processName: ProcessName): Future[List[ScenarioActivity]] = Future.successful(List())

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
