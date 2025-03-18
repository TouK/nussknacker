package pl.touk.nussknacker.ui.process.migrate

import cats.data.EitherT
import cats.instances.future._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestProcessUtil}
import pl.touk.nussknacker.test.utils.domain.TestFactory.{flinkProcessValidator, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapterService}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
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
    import scala.concurrent.ExecutionContext.Implicits.global
    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = localScenarioDescriptionVersion - 1
    val remoteEnvironment: MockRemoteEnvironment with LastSentMigrateScenarioRequest =
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
    import scala.concurrent.ExecutionContext.Implicits.global
    val localScenarioDescriptionVersion = migrationApiAdapterService.getCurrentApiVersion
    val remoteEnvironment: MockRemoteEnvironment with LastSentMigrateScenarioRequest =
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
    import scala.concurrent.ExecutionContext.Implicits.global
    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = localScenarioDescriptionVersion + 1
    val remoteEnvironment: MockRemoteEnvironment with LastSentMigrateScenarioRequest =
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

  // TODO: separate test to check response without labels to test decoder fallback
  it should "test migration" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    val remoteEnvironment = environmentForTestMigration(
      processes = ProcessTestData.validScenarioDetailsForMigrations :: Nil,
      fragments = TestProcessUtil.wrapWithDetailsForMigration(
        CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment),
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

  trait MockRemoteEnvironment extends StandardRemoteEnvironment {
    override def environmentId = "testEnv"

    def config: StandardRemoteEnvironmentConfig = StandardRemoteEnvironmentConfig(
      batchSize = 100
    )

    override implicit val materializer: Materializer = Materializer(system)

    override def testModelMigrations: TestModelMigrations = new TestModelMigrations(
      mapProcessingTypeDataProvider(
        "streaming" -> new ProcessModelMigrator(new TestMigrations(1, 2))
      ),
      mapProcessingTypeDataProvider("streaming" -> flinkProcessValidator)
    )

  }

  private trait LastSentMigrateScenarioRequest {
    var lastlySentMigrateScenarioRequest: Option[MigrateScenarioData] = None
  }

  private def remoteEnvironmentMock(
      scenarioDescriptionVersion: Int
  ) = new MockRemoteEnvironment with LastSentMigrateScenarioRequest {

    override protected def fetchRemoteMigrationScenarioDescriptionVersion(
        implicit ec: ExecutionContext
    ): EitherT[Future, NuDesignerError, Int] = {
      EitherT.rightT[Future, NuDesignerError](scenarioDescriptionVersion)
    }

    override protected def migrateScenario(
        migrateScenarioData: MigrateScenarioData
    )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {
      lastlySentMigrateScenarioRequest = Some(migrateScenarioData)
      Future.successful(Right(()))
    }
  }

  private def environmentForTestMigration(
      processes: List[ScenarioWithDetailsForMigrations],
      fragments: List[ScenarioWithDetailsForMigrations]
  ) = new MockRemoteEnvironment {

    private def allProcesses: List[ScenarioWithDetailsForMigrations] = processes ++ fragments

    override protected def fetchProcesses(
        implicit ec: ExecutionContext
    ): Future[Either[NuDesignerError, List[ScenarioWithDetailsForMigrations]]] = {
      Future.successful(Right(allProcesses.map(_.copy(scenarioGraph = None))))
    }

    override protected def fetchProcessesDetails(
        names: List[ProcessName]
    )(implicit ec: ExecutionContext): EitherT[Future, NuDesignerError, List[ScenarioWithDetailsForMigrations]] = {
      EitherT.rightT[Future, NuDesignerError](allProcesses.filter(p => names.contains(p.name)))
    }
  }

}
