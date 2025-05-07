package pl.touk.nussknacker.ui.process.newdeployment

import org.scalatest.{BeforeAndAfterEach, Inside}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.{
  MockableDeploymentManager,
  MockableDeploymentManagerConfigurator
}
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig.MaxActiveScenariosCount
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentManager,
  DeploymentStatus,
  DeploymentStatusName,
  ProblemDeploymentStatus
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestProcessingType
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestProcessingType.{
  Streaming1,
  Streaming2
}
import pl.touk.nussknacker.test.utils.domain.{TestFactory, TestProcessingTypeDataProviderFactory}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.validProcessWithName
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.limits.{GlobalLimitsConfig, LimitsService}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.{
  ScenarioStatusProvider => OldApproachScenarioStatusProvider
}
import pl.touk.nussknacker.ui.process.newdeployment.{ScenarioStatusProvider => NewApproachScenarioStatusProvider}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.{DeploymentEntityData, WithModifiedAt}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentService.{
  DeploymentForeignKeys,
  MaxActiveScenariosCountExceededError
}
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext
import scala.util.Failure

class DeploymentServiceSpec
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

  "request deployment and provide status for it" in {
    val scenario = validProcessWithName("validScenario")
    saveScenario(scenario)

    val deploymentId = DeploymentId.generate
    val user         = adminUser()
    deploymentService
      .runDeployment(
        RunDeploymentCommand(deploymentId, scenario.name, NodesDeploymentData.empty, user)
      )
      .futureValue
      .rightValue

    val status = deploymentService.getDeploymentStatus(deploymentId)(user).futureValue.rightValue
    status.value shouldEqual DeploymentStatus.DuringDeploy
  }

  "deployment which ended up with failure during request should has problem status" in {
    val scenario = validProcessWithName("scenarioCausingFailure")
    saveScenario(scenario)
    val deploymentId = DeploymentId.generate
    streaming1DeploymentManagerConfigurator.configureDeploymentResults(
      Map(deploymentId -> Failure(new Exception("Some failure during deployment")))
    )

    val user = adminUser()
    deploymentService
      .runDeployment(
        RunDeploymentCommand(deploymentId, scenario.name, NodesDeploymentData.empty, user)
      )
      .futureValue
      .rightValue

    eventually {
      val status = deploymentService.getDeploymentStatus(deploymentId)(user).futureValue.rightValue
      status.value.name shouldEqual DeploymentStatusName.problemStatusName
    }
  }

  "should allow to deploy scenario when active scenarios count is less than the limit" when {
    "one processing type is considered" when {
      "1st scenario is running, and the 2nd scenario is not deployed" in {
        prepareDeployedScenario("scenario1")
        prepareNotDeployedScenario("scenario2")

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running, and the 2nd scenario is cancelled" in {
        prepareDeployedScenario("scenario1")
        prepareCanceledScenario("scenario2")

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running, and the 2nd scenario is during cancel" in {
        prepareDeployedScenario("scenario1")
        prepareDuringCancelScenario("scenario2")

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running, and the 2nd scenario is finished" in {
        prepareDeployedScenario("scenario1")
        prepareFinishedScenario("scenario2")

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running, and the 2nd scenario is problem" in {
        prepareDeployedScenario("scenario1")
        prepareScenarioWithProblem("scenario2")

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
    }
    "two processing types are considered" when {
      "1st scenario is running (in streaming1), and the 2nd scenario is not deployed (in streaming2)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareNotDeployedScenario("scenario2", Streaming2)

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running (in streaming1), and the 2nd scenario is cancelled (in streaming2)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareCanceledScenario("scenario2", Streaming2)

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running (in streaming1), and the 2nd scenario is during cancel (in streaming2)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareDuringCancelScenario("scenario2", Streaming2)

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running (in streaming1), and the 2nd scenario is finished (in streaming2)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareFinishedScenario("scenario2", Streaming2)

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
      "1st scenario is running (in streaming1), and the 2nd scenario is problem (in streaming2)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareScenarioWithProblem("scenario2", Streaming2)

        val result = deployExampleScenario("scenario3")

        inside(result) { case Right(DeploymentForeignKeys(_, _)) =>
        }
      }
    }
  }

  "should not allow more scenarios than active scenario limits to be used" when {
    "one processing type is considered" when {
      "1st scenario is running, and the 2nd scenario is running, and the 3rd scenario is not deployed" in {
        prepareDeployedScenario("scenario1")
        prepareDeployedScenario("scenario2")
        prepareNotDeployedScenario("scenario3")

        val result = deployExampleScenario("scenario4")

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
      "1st scenario is running, and the 2nd scenario is during deploy, and the 3rd scenario is not deployed" in {
        prepareDeployedScenario("scenario1")
        prepareDuringDeployScenario("scenario2")
        prepareNotDeployedScenario("scenario3")

        val result = deployExampleScenario("scenario4")

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
      "1st scenario is running, and the 2nd scenario is restarting, and the 3rd scenario is not deployed" in {
        prepareDeployedScenario("scenario1")
        prepareRestartingScenario("scenario2")
        prepareNotDeployedScenario("scenario3")

        val result = deployExampleScenario("scenario4")

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
      "1st scenario is being redeployed, when the 2nd scenario is running" in {
        val scenario1 = prepareDeployedScenario("scenario1")
        prepareDeployedScenario("scenario2")

        val result = redeployExampleScenario(scenario1)

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
    }
    "two processing types are considered" when {
      "1st scenario is running (in streaming1), and the 2nd scenario is running (in streaming2), and the 3rd scenario is not deployed (in streaming1)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareDeployedScenario("scenario2", Streaming2)
        prepareNotDeployedScenario("scenario3", Streaming1)

        val result = deployExampleScenario("scenario4")

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
      "1st scenario is running (in streaming1), and the 2nd scenario is during deploy (in streaming2), and the 3rd scenario is not deployed (in streaming1)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareDuringDeployScenario("scenario2", Streaming2)
        prepareNotDeployedScenario("scenario3", Streaming1)

        val result = deployExampleScenario("scenario4")

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
      "1st scenario is running (in streaming1), and the 2nd scenario is restarting (in streaming2), and the 3rd scenario is not deployed (in streaming1)" in {
        prepareDeployedScenario("scenario1", Streaming1)
        prepareRestartingScenario("scenario2", Streaming2)
        prepareNotDeployedScenario("scenario3", Streaming1)

        val result = deployExampleScenario("scenario4")

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
      "1st scenario is being redeployed (streaming1), when the 2nd scenario is running (streaming2)" in {
        val scenario1 = prepareDeployedScenario("scenario1", Streaming1)
        prepareDeployedScenario("scenario2", Streaming2)

        val result = redeployExampleScenario(scenario1)

        inside(result) { case Left(MaxActiveScenariosCountExceededError(2)) =>
        }
      }
    }
  }

  override def beforeEach(): Unit = {
    streaming1DeploymentManagerConfigurator.clean()
    streaming2DeploymentManagerConfigurator.clean()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    executionContextWithIORuntime.close()
    super.afterAll()
  }

  override protected val dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private val writeScenarioRepository = newWriteProcessRepository(testDbRef, clock, modelVersions = None)
  private val deploymentRepository    = newDeploymentRepository(testDbRef, clock)

  private lazy val streaming1DeploymentManagerConfigurator = new MockableDeploymentManagerConfigurator()
  private lazy val streaming2DeploymentManagerConfigurator = new MockableDeploymentManagerConfigurator()

  private lazy val deploymentService = {
    val clock                      = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC)
    val scenarioMetadataRepository = newScenarioMetadataRepository(testDbRef)
    val streaming1DeploymentManager =
      new MockableDeploymentManager(streaming1DeploymentManagerConfigurator, modelDataProviderOpt = None)
    val streaming2DeploymentManager =
      new MockableDeploymentManager(streaming2DeploymentManagerConfigurator, modelDataProviderOpt = None)
    val processingTypeDataProvider: ProcessingTypeDataProvider[DeploymentManager, _] =
      TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
        Map(
          Streaming1.stringify -> ValueWithRestriction.anyUser(streaming1DeploymentManager),
          Streaming2.stringify -> ValueWithRestriction.anyUser(streaming2DeploymentManager)
        )
      )
    val deploymentManagerDispatcher = new DeploymentManagerDispatcher(
      processingTypeDataProvider,
      newFutureFetchingScenarioRepository(testDbRef)
    )

    val oldApproachScenarioStatusProvider = new OldApproachScenarioStatusProvider(
      new EngineSideDeploymentStatusesProvider(deploymentManagerDispatcher, scenarioStateTimeout = None),
      deploymentManagerDispatcher,
      newFetchingProcessRepository(testDbRef),
      newActionProcessRepository(testDbRef),
      dbioRunner
    )

    val newApproachScenarioStatusProvider = new NewApproachScenarioStatusProvider(
      processingTypeDataProvider,
      TestFactory.newDeploymentRepository(testDbRef, clock),
      dbioRunner
    )

    val supportedProcessingTypes = List(Streaming1.stringify, Streaming2.stringify)
    val processingTypeLimitsConfig =
      LimitsConfig.default.copy(maxActiveScenariosCount = Some(MaxActiveScenariosCount(2)))
    val globalLimitsConfig = GlobalLimitsConfig.default.copy(
      maxActiveScenariosCount = Some(GlobalLimitsConfig.MaxActiveScenariosCount(2))
    )

    new DeploymentService(
      scenarioMetadataRepository = scenarioMetadataRepository,
      scenarioGraphVersionService = newScenarioGraphVersionService(testDbRef, supportedProcessingTypes),
      deploymentRepository = deploymentRepository,
      dmDispatcher = deploymentManagerDispatcher,
      dbioRunner = dbioRunner,
      clock = clock,
      additionalComponentConfigs = additionalComponentConfigsByProcessingType,
      limitsService = new LimitsService(
        globalLimitsConfig = globalLimitsConfig,
        perProcessingTypesLimitsProvider = mapProcessingTypeDataProvider(
          supportedProcessingTypes.map(pt => (pt, processingTypeLimitsConfig)): _*
        ),
        oldDeploymentsApproachScenarioStatusProvider = oldApproachScenarioStatusProvider,
        newDeploymentsApproachScenarioStatusProvider = newApproachScenarioStatusProvider,
      )
    )
  }

  private def prepareNotDeployedScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, deploymentStatus = None)
  }

  private def prepareDeployedScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(DeploymentStatus.Running))
  }

  private def prepareDuringDeployScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(DeploymentStatus.DuringDeploy))
  }

  private def prepareRestartingScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(DeploymentStatus.Restarting))
  }

  private def prepareCanceledScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(DeploymentStatus.Canceled))
  }

  private def prepareDuringCancelScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(DeploymentStatus.DuringCancel))
  }

  private def prepareFinishedScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(DeploymentStatus.Finished))
  }

  private def prepareScenarioWithProblem(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    prepareScenarioInStatus(scenarioName, processingType, Some(ProblemDeploymentStatus("PROBLEM")))
  }

  private def prepareScenarioInStatus(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1,
      deploymentStatus: Option[DeploymentStatus] = None
  ): ProcessIdWithName = {
    val creator  = adminUser()
    val scenario = saveScenario(validProcessWithName(scenarioName), creator, processingType)
    deploymentStatus.foreach(configureScenarioDeploymentStatus(scenario.id, creator, _))
    scenario
  }

  private def saveScenario(
      scenario: CanonicalProcess,
      createdByUser: LoggedUser = adminUser(),
      processingType: TestProcessingType = Streaming1
  ): ProcessIdWithName = {
    val scenarioId = writeScenarioRepository
      .saveNewProcess(
        CreateProcessAction(
          processName = scenario.name,
          category = "fooCategory",
          canonicalProcess = scenario,
          processingType = processingType.stringify,
          isFragment = false,
        )
      )(createdByUser)
      .dbioActionValues
      .get
      .processId
    ProcessIdWithName(scenarioId, scenario.name)
  }

  private def configureScenarioDeploymentStatus(
      scenarioId: ProcessId,
      deployedByUser: LoggedUser,
      deploymentStatus: DeploymentStatus
  ): Unit = {
    val now = Timestamp.from(clock.instant())
    deploymentRepository
      .saveDeployment(
        DeploymentEntityData(
          id = DeploymentId.generate,
          scenarioId = scenarioId,
          createdAt = now,
          createdBy = deployedByUser.username,
          statusWithModifiedAt = WithModifiedAt(deploymentStatus, now)
        )
      )
      .dbioActionValues match {
      case Left(error) => throw new IllegalStateException(s"Unexpected error: $error")
      case Right(())   =>
    }
  }

  private def deployExampleScenario(
      scenarioName: String,
      processingType: TestProcessingType = Streaming1
  ): Either[DeploymentService.RunDeploymentError, DeploymentForeignKeys] = {
    val scenario = saveScenario(validProcessWithName(scenarioName), adminUser(), processingType)
    runDeployment(scenario, adminUser())
  }

  private def redeployExampleScenario(
      scenario: ProcessIdWithName
  ): Either[DeploymentService.RunDeploymentError, DeploymentForeignKeys] = {
    runDeployment(scenario, adminUser())
  }

  private def runDeployment(scenario: ProcessIdWithName, deployingByUser: LoggedUser) = {
    deploymentService
      .runDeployment(
        RunDeploymentCommand(DeploymentId.generate, scenario.name, NodesDeploymentData.empty, deployingByUser)
      )
      .futureValue
  }

}
