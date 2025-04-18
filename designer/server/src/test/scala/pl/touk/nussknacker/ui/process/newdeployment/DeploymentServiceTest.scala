package pl.touk.nussknacker.ui.process.newdeployment

import org.scalatest.{BeforeAndAfterEach, Inside}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.touk.nussknacker.development.manager.BasicStatusDetails
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.ProcessingTypeConfig.ActiveScenariosLimit
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, ProblemDeploymentStatus, StateStatus}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory, TestProcessingTypeDataProviderFactory}
import pl.touk.nussknacker.test.utils.domain.TestFactory.{
  adminUser,
  newActionProcessRepository,
  newFetchingProcessRepository
}
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService.{
  ActiveScenariosLimitExceededError,
  DeploymentForeignKeys
}
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext
import scala.util.Failure

class DeploymentServiceTest
    extends AnyWordSpec
    with Matchers
    with Inside
    with PatientScalaFutures
    with WithHsqlDbTesting
    with WithClock
    with DBIOActionValues
    with EitherValuesDetailedMessage
    with BeforeAndAfterEach {

  private implicit val executionContextWithIORuntime: ExecutionContextWithIORuntimeAdapter =
    ExecutionContextWithIORuntimeAdapter.unsafeCreateFrom(ExecutionContext.global)
  import executionContextWithIORuntime.ioRuntime

  override protected val dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private val writeScenarioRepository = TestFactory.newWriteProcessRepository(testDbRef, clock, modelVersions = None)

  private val service = {
    val clock                      = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC)
    val scenarioMetadataRepository = TestFactory.newScenarioMetadataRepository(testDbRef)
    val deploymentManager          = new MockableDeploymentManager(modelDataProviderOpt = None)
    val deploymentManagerDispatcher = new DeploymentManagerDispatcher(
      TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
        Map(Streaming.stringify -> ValueWithRestriction.anyUser(deploymentManager))
      ),
      TestFactory.newFutureFetchingScenarioRepository(testDbRef)
    )

    val scenarioStatusProvider = {
      new ScenarioStatusProvider(
        new EngineSideDeploymentStatusesProvider(deploymentManagerDispatcher, scenarioStateTimeout = None),
        deploymentManagerDispatcher,
        newFetchingProcessRepository(testDbRef),
        newActionProcessRepository(testDbRef),
        dbioRunner
      )
    }

    new DeploymentService(
      scenarioMetadataRepository,
      TestFactory.newScenarioGraphVersionService(testDbRef),
      TestFactory.newDeploymentRepository(testDbRef, clock),
      deploymentManagerDispatcher,
      dbioRunner,
      clock,
      TestFactory.additionalComponentConfigsByProcessingType,
      scenarioStatusProvider,
      TestFactory.mapProcessingTypeDataProvider(Streaming.stringify -> Some(ActiveScenariosLimit(2)))
    )
  }

  "request deployment and provide status for it" in {
    val scenarioName = ProcessName("validScenario")
    val scenario     = ProcessTestData.validProcessWithName(scenarioName)
    saveSampleScenario(scenario)

    val deploymentId = DeploymentId.generate
    val user         = adminUser()
    service
      .runDeployment(
        RunDeploymentCommand(deploymentId, scenarioName, NodesDeploymentData.empty, user)
      )
      .futureValue
      .rightValue

    val status = service.getDeploymentStatus(deploymentId)(user).futureValue.rightValue
    status.value shouldEqual DeploymentStatus.DuringDeploy
  }

  "deployment which ended up with failure during request should has problem status" in {
    val scenarioName = ProcessName("scenarioCausingFailure")
    val scenario     = ProcessTestData.validProcessWithName(scenarioName)
    saveSampleScenario(scenario)
    val deploymentId = DeploymentId.generate
    MockableDeploymentManager.configureDeploymentResults(
      Map(deploymentId -> Failure(new Exception("Some failure during deployment")))
    )

    val user = adminUser()
    service
      .runDeployment(
        RunDeploymentCommand(deploymentId, scenarioName, NodesDeploymentData.empty, user)
      )
      .futureValue
      .rightValue

    eventually {
      val status = service.getDeploymentStatus(deploymentId)(user).futureValue.rightValue
      status.value.name shouldEqual ProblemDeploymentStatus.name
    }
  }

  "should allow to deploy scenario when active scenarios count is less than the limit" when {
    "1st scenario is running, and the 2nd scenario is not deployed" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.NotDeployed, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario3"))

      inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
      }
    }
    "1st scenario is running, and the 2nd scenario is cancelled" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.Canceled, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario3"))

      inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
      }
    }
    "1st scenario is running, and the 2nd scenario is during cancel" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.DuringCancel, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario3"))

      inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
      }
    }
    "1st scenario is running, and the 2nd scenario is finished" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.Finished, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario3"))

      inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
      }
    }
    "1st scenario is running, and the 2nd scenario is problem" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(StateStatus("PROBLEM"), version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario3"))

      inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
      }
    }
  }

  "should not allow more scenarios than active scenario limits to be used" when {
    "1st scenario is running, and the 2nd scenario is running, and the 3rd scenario is not deployed" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario3" -> BasicStatusDetails(SimpleStateStatus.NotDeployed, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario4"))

      inside(result) { case Left(ActiveScenariosLimitExceededError(2)) =>
      }
    }
    "1st scenario is running, and the 2nd scenario is during deploy, and the 3rd scenario is not deployed" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.DuringDeploy, version = Some(VersionId(1))),
          "scenario3" -> BasicStatusDetails(SimpleStateStatus.NotDeployed, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario4"))

      inside(result) { case Left(ActiveScenariosLimitExceededError(2)) =>
      }
    }
    "1st scenario is running, and the 2nd scenario is restarting, and the 3rd scenario is not deployed" in {
      MockableDeploymentManager.configureScenarioStatuses(
        Map(
          "scenario1" -> BasicStatusDetails(SimpleStateStatus.Running, version = Some(VersionId(1))),
          "scenario2" -> BasicStatusDetails(SimpleStateStatus.Restarting, version = Some(VersionId(1))),
          "scenario3" -> BasicStatusDetails(SimpleStateStatus.NotDeployed, version = Some(VersionId(1))),
        )
      )

      val result = deployExampleScenario(ProcessName("scenario4"))

      inside(result) { case Left(ActiveScenariosLimitExceededError(2)) =>
      }
    }
  }

  override def beforeEach(): Unit = {
    MockableDeploymentManager.clean()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    executionContextWithIORuntime.close()
    super.afterAll()
  }

  private def saveSampleScenario(scenario: CanonicalProcess) = {
    writeScenarioRepository
      .saveNewProcess(
        CreateProcessAction(
          processName = scenario.name,
          category = "fooCategory",
          canonicalProcess = scenario,
          processingType = Streaming.stringify,
          isFragment = false,
        )
      )(adminUser())
      .dbioActionValues
  }

  private def deployExampleScenario(scenarioName: ProcessName) = {
    val scenario = ProcessTestData.validProcessWithName(scenarioName)
    saveSampleScenario(scenario)

    service
      .runDeployment(
        RunDeploymentCommand(DeploymentId.generate, scenarioName, NodesDeploymentData.empty, adminUser())
      )
      .futureValue
  }

}
