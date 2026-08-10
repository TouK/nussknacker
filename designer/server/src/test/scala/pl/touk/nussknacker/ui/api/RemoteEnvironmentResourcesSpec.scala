package pl.touk.nussknacker.ui.api

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import org.apache.pekko.http.scaladsl.server
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.scalatest.{BeforeAndAfterEach, Inside}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
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
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{
  ScenarioActivity,
  ScenarioActivityComment,
  ScenarioActivityCommentContent,
  ScenarioActivityType
}
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.{VersionsWithDifferences, VersionWithDifference}
import pl.touk.nussknacker.ui.process.migrate.{
  RemoteEnvironment,
  RemoteEnvironmentCommunicationError,
  RemoteScenarioVersions,
  TestMigrationResult
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.{Difference, NodeNotPresentInCurrent, NodeNotPresentInOther}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
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

  private def remoteEnvironmentRoute(remoteEnvironment: RemoteEnvironment, permissions: Permission.Permission*) =
    withPermissions(
      new RemoteEnvironmentResources(
        remoteEnvironment,
        processService,
        processAuthorizer,
        scenarioActivityRepository,
        dbioRunner,
        clock,
      ),
      permissions: _*
    )

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
    val difference = Map(
      "node1" -> NodeNotPresentInCurrent(
        "node1",
        Filter(NodeId("node1"), NodeName("node1"), Expression.spel("#input == 4"))
      )
    )
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

    val difference = NodeNotPresentInOther("a", Filter(NodeId("a"), NodeName("a"), "".spel))

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

  it should "send the local scenario graph to the remote environment once and relay its answer" in {
    val sentGraph = new AtomicReference[ScenarioGraph]()
    val calls     = new AtomicInteger()
    val remoteAnswer = VersionsWithDifferences(
      versions = List(VersionWithDifference(VersionId(7), List("Node 'filter1' modified"), differencesUnknown = false))
    )

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def versionsWithDifferences(
          pName: ProcessName,
          scenarioGraph: ScenarioGraph,
          limit: Int
      ): Future[Option[VersionsWithDifferences]] = {
        sentGraph.set(scenarioGraph)
        calls.incrementAndGet()
        Future.successful(Some(remoteAnswer))
      }
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[VersionsWithDifferences] shouldBe remoteAnswer
        sentGraph.get().nodes.map(_.id) shouldBe ProcessTestData.validScenarioGraph.nodes.map(_.id)
        calls.get() shouldBe 1
      }
    }
  }

  it should "report that versions could not be compared when the remote environment cannot answer" in {
    val remoteEnvironment = new MockRemoteEnvironment() {
      override def versionsWithDifferences(
          pName: ProcessName,
          scenarioGraph: ScenarioGraph,
          limit: Int
      ): Future[Option[VersionsWithDifferences]] = Future.successful(None)
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val result = responseAs[VersionsWithDifferences]
        result.versions shouldBe empty
        result.remoteUnavailable shouldBe Some(true)
      }
    }
  }

  it should "relay the version comments the remote environment reports, compared or not" in {
    val remoteAnswer = VersionsWithDifferences(
      versions = List(
        VersionWithDifference(VersionId(7), List("Node 'filter1' modified"), differencesUnknown = false),
        VersionWithDifference(VersionId(8), List("Node 'filter2' modified"), differencesUnknown = false),
      ),
      // version 99 is one the differences answer says nothing about - not compared, or not differing
      versionComments = Some(Map(7L -> "restart", 99L -> "an older version's comment"))
    )

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def versionsWithDifferences(
          pName: ProcessName,
          scenarioGraph: ScenarioGraph,
          limit: Int
      ): Future[Option[VersionsWithDifferences]] = Future.successful(Some(remoteAnswer))
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val result = responseAs[VersionsWithDifferences]
        // the remote's own comments, not this environment's - they are passed through untouched
        result.versionComments shouldBe Some(Map(7L -> "restart", 99L -> "an older version's comment"))
      }
    }
  }

  it should "not expose remote versions to a user without read access to the scenario" in {
    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(
          RemoteScenarioVersions(
            List(ScenarioVersion(VersionId(1), Instant.now(), "user")),
            remoteUnavailable = false
          )
        )
    }

    val routeWithoutReadPermission = remoteEnvironmentRoute(remoteEnvironment)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/versions") ~> routeWithoutReadPermission ~> check {
        rejection shouldBe server.AuthorizationFailedRejection
      }
    }
  }

  it should "not expose remote versions with differences to a user without read access to the scenario" in {
    val routeWithoutReadPermission = remoteEnvironmentRoute(new MockRemoteEnvironment)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(
        s"/remoteEnvironment/$processName/2/versions-with-differences"
      ) ~> routeWithoutReadPermission ~> check {
        rejection shouldBe server.AuthorizationFailedRejection
      }
    }
  }

  // The remote bounds its own work with this, so it has to arrive there rather than being applied to the
  // answer after the remote has already walked its whole history.
  it should "pass the comparison limit through to the remote environment" in {
    val sentLimit = new AtomicInteger()

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def versionsWithDifferences(
          pName: ProcessName,
          scenarioGraph: ScenarioGraph,
          limit: Int
      ): Future[Option[VersionsWithDifferences]] = {
        sentLimit.set(limit)
        Future.successful(Some(VersionsWithDifferences(Nil)))
      }
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences?limit=7") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        sentLimit.get() shouldBe 7
      }
    }
  }

  it should "ask the remote environment for a bounded number of versions when no limit is given" in {
    val sentLimit = new AtomicInteger()

    val remoteEnvironment = new MockRemoteEnvironment() {
      override def versionsWithDifferences(
          pName: ProcessName,
          scenarioGraph: ScenarioGraph,
          limit: Int
      ): Future[Option[VersionsWithDifferences]] = {
        sentLimit.set(limit)
        Future.successful(Some(VersionsWithDifferences(Nil)))
      }
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/2/versions-with-differences") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        sentLimit.get() shouldBe VersionsWithDifferencesService.DefaultVersionsCompared
      }
    }
  }

  it should "report that the remote environment is unavailable instead of an empty list of versions" in {
    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(RemoteScenarioVersions(List.empty, remoteUnavailable = true))
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/versions") ~> route ~> check {
        status shouldEqual StatusCodes.BadGateway
      }
    }
  }

  it should "return the remote environment's versions when it can be reached" in {
    val remoteVersion = ScenarioVersion(VersionId(3), Instant.parse("2024-01-17T14:21:17Z"), "some user")
    val remoteEnvironment = new MockRemoteEnvironment() {
      override def processVersions(pName: ProcessName): Future[RemoteScenarioVersions] =
        Future.successful(RemoteScenarioVersions(List(remoteVersion), remoteUnavailable = false))
    }

    val route = remoteEnvironmentRoute(remoteEnvironment, Permission.Read)

    saveCanonicalProcess(ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processName/versions") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[List[ScenarioVersion]] shouldBe List(remoteVersion)
      }
    }
  }

  it should "not fail in comparing environments if process does not exist in the other one" in {
    import pl.touk.nussknacker.engine.spel.SpelExtension._
    val processId1 = ProcessName("proc1")
    val processId2 = ProcessName("proc2")

    val difference = NodeNotPresentInOther("a", Filter(NodeId("a"), NodeName("a"), "".spel))

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

    override def versionsWithDifferences(
        processName: ProcessName,
        scenarioGraph: ScenarioGraph,
        limit: Int
    ): Future[Option[VersionsWithDifferences]] = Future.successful(None)

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
