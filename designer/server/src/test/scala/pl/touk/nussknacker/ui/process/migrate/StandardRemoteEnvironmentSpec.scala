package pl.touk.nussknacker.ui.process.migrate

import cats.data.EitherT
import cats.instances.future._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestProcessUtil}
import pl.touk.nussknacker.test.utils.domain.TestFactory.{flinkProcessValidator, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivity
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapterService}
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.VersionsWithDifferences
import pl.touk.nussknacker.ui.process.migrate.StandardRemoteEnvironmentSpec._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.implicitConversions

class StandardRemoteEnvironmentSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with FailFastCirceSupport
    with EitherValuesDetailedMessage
    with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("nussknacker-designer")
  implicit val user: LoggedUser    = RealLoggedUser("1", "test")

  val migrationApiAdapterService = new MigrationApiAdapterService()

  it should "request to migrate valid scenario when remote scenario description version is lower than local scenario description version" in {
    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = localScenarioDescriptionVersion - 1
    val remoteEnvironment: MockRemoteEnvironment =
      remoteEnvironmentMock(scenarioDescriptionVersion = remoteScenarioDescriptionVersion)

    whenReady(
      remoteEnvironment.migrate(
        ProcessTestData.sampleScenarioParameters.processingMode,
        ProcessTestData.sampleScenarioParameters.engineSetupName,
        ProcessTestData.sampleScenarioParameters.category,
        ProcessTestData.sampleScenarioLabels,
        ProcessTestData.validScenarioGraph,
        ProcessTestData.versionId,
        ProcessTestData.sampleProcessName,
        false
      )
    ) { res =>
      res shouldBe Right(())
      remoteEnvironment.lastlySentMigrateScenarioRequest match {
        case Some(migrateScenarioRequest) =>
          migrateScenarioRequest.currentVersion shouldBe remoteScenarioDescriptionVersion
        case _ => fail("lastly sent migrate scenario request should be non empty")
      }
    }
  }

  it should "request to migrate valid scenario when remote scenario description version is the same as local scenario description version" in {
    val localScenarioDescriptionVersion = migrationApiAdapterService.getCurrentApiVersion
    val remoteEnvironment: MockRemoteEnvironment =
      remoteEnvironmentMock(scenarioDescriptionVersion = localScenarioDescriptionVersion)

    whenReady(
      remoteEnvironment.migrate(
        ProcessTestData.sampleScenarioParameters.processingMode,
        ProcessTestData.sampleScenarioParameters.engineSetupName,
        ProcessTestData.sampleScenarioParameters.category,
        ProcessTestData.sampleScenarioLabels,
        ProcessTestData.validScenarioGraph,
        ProcessTestData.versionId,
        ProcessTestData.sampleProcessName,
        false
      )
    ) { res =>
      res shouldBe Right(())
      remoteEnvironment.lastlySentMigrateScenarioRequest match {
        case Some(migrateScenarioRequest) =>
          migrateScenarioRequest.currentVersion shouldBe localScenarioDescriptionVersion
        case _ => fail("lastly sent migrate scenario request should be non empty")
      }
    }
  }

  it should "request to migrate valid scenario when remote scenario description version is higher than local scenario description version" in {
    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = localScenarioDescriptionVersion + 1
    val remoteEnvironment: MockRemoteEnvironment =
      remoteEnvironmentMock(scenarioDescriptionVersion = remoteScenarioDescriptionVersion)

    whenReady(
      remoteEnvironment.migrate(
        ProcessTestData.sampleScenarioParameters.processingMode,
        ProcessTestData.sampleScenarioParameters.engineSetupName,
        ProcessTestData.sampleScenarioParameters.category,
        ProcessTestData.sampleScenarioLabels,
        ProcessTestData.validScenarioGraph,
        ProcessTestData.versionId,
        ProcessTestData.sampleProcessName,
        false
      )
    ) { res =>
      res shouldBe Right(())
      remoteEnvironment.lastlySentMigrateScenarioRequest match {
        case Some(migrateScenarioRequest) =>
          migrateScenarioRequest.currentVersion shouldBe localScenarioDescriptionVersion
        case _ => fail("lastly sent migrate scenario request should be non empty")
      }
    }
  }

  it should "test migration" in {
    val remoteEnvironment = environmentForTestMigration(
      processes = ProcessTestData.validScenarioDetailsForMigrations :: Nil,
      fragments = TestProcessUtil.wrapWithDetailsForMigration(
        ProcessTestData.sampleFragment.toScenarioGraph,
        ProcessTestData.sampleFragment.name
      ) :: Nil
    )

    val migrationResult = remoteEnvironment
      .testMigration(
        batchingExecutionContext = ExecutionContext.global
      )
      .futureValueEnsuringInnerException(10 seconds)
      .rightValue

    migrationResult should have size 2
    migrationResult.map(
      _.processName
    ) should contain only (ProcessTestData.validScenarioDetailsForMigrations.name, ProcessTestData.sampleFragment.name)
  }

  override protected def afterAll(): Unit = {
    system.terminate().futureValue
    super.afterAll()
  }

}

object StandardRemoteEnvironmentSpec {

  private class MockRemoteEnvironment extends StandardRemoteEnvironment {

    override protected implicit val ec: ExecutionContext = ExecutionContext.global
    override protected val localEnvironmentId: String    = "localEnv"
    override protected val remoteEnvironmentId: String   = "remoteEnv"

    var lastlySentMigrateScenarioRequest: Option[MigrateScenarioData] = None

    def config: StandardRemoteEnvironmentConfig = StandardRemoteEnvironmentConfig(
      batchSize = 100
    )

    override def processVersions(processName: ProcessName): Future[RemoteScenarioVersions] = ???

    override def versionsWithDifferences(
        processName: ProcessName,
        scenarioGraph: ScenarioGraph,
        limit: Int
    ): Future[Option[VersionsWithDifferences]] = ???

    override def activities(processName: ProcessName): Future[List[ScenarioActivity]] = ???

    override def testModelMigrations: TestModelMigrations = new TestModelMigrations(
      mapProcessingTypeDataProvider(
        "streaming" -> new ProcessModelMigrator(new TestMigrations(1, 2))
      ),
      mapProcessingTypeDataProvider("streaming" -> flinkProcessValidator("streaming" :: Nil))
    )

    protected def fetchRemoteMigrationScenarioDescriptionVersion: FutureE[Int] = ???

    protected def migrateScenario(migrateScenarioData: MigrateScenarioData)(
        implicit loggedUser: LoggedUser
    ): FutureE[Unit] = ???

    protected def fetchProcesses: FutureE[List[ScenarioWithDetailsForMigrations]] = ???

    protected def fetchProcessVersion(
        name: ProcessName,
        remoteProcessVersion: Option[VersionId]
    ): FutureE[ScenarioWithDetailsForMigrations] = ???

    protected def fetchProcessesDetails(names: List[ProcessName]): FutureE[List[ScenarioWithDetailsForMigrations]] = ???

  }

  private def remoteEnvironmentMock(
      scenarioDescriptionVersion: Int
  ): MockRemoteEnvironment = new MockRemoteEnvironment {

    override protected def fetchRemoteMigrationScenarioDescriptionVersion: FutureE[Int] = {
      EitherT.rightT[Future, NuDesignerError](scenarioDescriptionVersion)
    }

    override protected def migrateScenario(
        migrateScenarioData: MigrateScenarioData
    )(implicit loggedUser: LoggedUser): FutureE[Unit] = {
      lastlySentMigrateScenarioRequest = Some(migrateScenarioData)
      EitherT.rightT[Future, NuDesignerError](())
    }
  }

  private def environmentForTestMigration(
      processes: List[ScenarioWithDetailsForMigrations],
      fragments: List[ScenarioWithDetailsForMigrations]
  ): MockRemoteEnvironment = new MockRemoteEnvironment {

    private def allProcesses: List[ScenarioWithDetailsForMigrations] = processes ++ fragments

    override protected def fetchProcesses: FutureE[List[ScenarioWithDetailsForMigrations]] = {
      EitherT.rightT[Future, NuDesignerError](allProcesses.map(_.copy(scenarioGraph = None)))
    }

    override protected def fetchProcessesDetails(
        names: List[ProcessName]
    ): FutureE[List[ScenarioWithDetailsForMigrations]] = {
      EitherT.rightT[Future, NuDesignerError](allProcesses.filter(p => names.contains(p.name)))
    }
  }

}
