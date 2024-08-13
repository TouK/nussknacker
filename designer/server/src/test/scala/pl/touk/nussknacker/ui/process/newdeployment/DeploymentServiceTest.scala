package pl.touk.nussknacker.ui.process.newdeployment

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, ProblemDeploymentStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

class DeploymentServiceTest
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with WithHsqlDbTesting
    with DBIOActionValues
    with EitherValuesDetailedMessage
    with BeforeAndAfterEach {

  override protected val dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private val writeScenarioRepository = TestFactory.newWriteProcessRepository(testDbRef, modelVersions = None)

  private val service = {
    val clock                      = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC)
    val scenarioMetadataRepository = TestFactory.newScenarioMetadataRepository(testDbRef)
    new DeploymentService(
      scenarioMetadataRepository,
      TestFactory.newScenarioGraphVersionService(testDbRef),
      TestFactory.newDeploymentRepository(testDbRef, clock),
      new DeploymentManagerDispatcher(
        ProcessingTypeDataProvider.withEmptyCombinedData(
          Map(Streaming.stringify -> ValueWithRestriction.anyUser(new MockableDeploymentManager(modelDataOpt = None)))
        ),
        TestFactory.newFutureFetchingScenarioRepository(testDbRef)
      ),
      dbioRunner,
      clock
    )
  }

  test("request deployment and provide status for it") {
    val scenarioName = ProcessName("validScenario")
    val scenario     = ProcessTestData.validProcessWithName(scenarioName)
    saveSampleScenario(scenario)

    val deploymentId = DeploymentId.generate
    val user         = TestFactory.adminUser()
    service
      .runDeployment(
        RunDeploymentCommand(deploymentId, scenarioName, NodesDeploymentData.empty, user)
      )
      .futureValue
      .rightValue

    val status = service.getDeploymentStatus(deploymentId)(user).futureValue.rightValue
    status.value shouldEqual DeploymentStatus.DuringDeploy
  }

  test("deployment which ended up with failure during request should has problem status") {
    val scenarioName = ProcessName("scenarioCausingFailure")
    val scenario     = ProcessTestData.validProcessWithName(scenarioName)
    saveSampleScenario(scenario)
    val deploymentId = DeploymentId.generate
    MockableDeploymentManager.configureDeploymentResults(
      Map(deploymentId -> Failure(new Exception("Some failure during deployment")))
    )

    val user = TestFactory.adminUser()
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

  private def saveSampleScenario(scenario: CanonicalProcess) = {
    writeScenarioRepository
      .saveNewProcess(
        CreateProcessAction(
          processName = scenario.name,
          category = "fooCategory",
          canonicalProcess = scenario,
          processingType = Streaming.stringify,
          isFragment = false,
          forwardedUserName = None
        )
      )(TestFactory.adminUser())
      .dbioActionValues
  }

  override def beforeEach(): Unit = {
    MockableDeploymentManager.clean()
    super.beforeEach()
  }

}
