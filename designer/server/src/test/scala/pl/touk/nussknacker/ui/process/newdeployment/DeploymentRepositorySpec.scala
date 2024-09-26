package pl.touk.nussknacker.ui.process.newdeployment

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestCategory
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.{DeploymentEntityData, WithModifiedAt}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

import java.sql.Timestamp
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class DeploymentRepositorySpec
    extends AnyFunSuite
    with Matchers
    with WithHsqlDbTesting
    with WithClock
    with DBIOActionValues
    with PatientScalaFutures
    with OptionValues
    with EitherValuesDetailedMessage
    with TableDrivenPropertyChecks {

  override protected def dbioRunner: DBIOActionRunner = new DBIOActionRunner(testDbRef)

  private val deploymentRepository =
    new DeploymentRepository(testDbRef, Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC))

  private val scenarioRepository = TestFactory.newWriteProcessRepository(testDbRef, clock)

  private lazy val sampleScenarioId = scenarioRepository
    .saveNewProcess(
      CreateProcessAction(
        processName = ProcessTestData.validProcess.name,
        category = TestCategory.Category1.stringify,
        canonicalProcess = ProcessTestData.validProcess,
        processingType = Streaming.stringify,
        isFragment = false,
        forwardedUserName = None
      )
    )(TestFactory.adminUser())
    .dbioActionValues
    .value
    .processId

  test("status should be updated only when changed") {
    forAll(
      Table(
        ("initial status", "to update status"),
        (DeploymentStatus.DuringDeploy, DeploymentStatus.DuringDeploy),
        (DeploymentStatus.DuringDeploy, DeploymentStatus.Running),
        (DeploymentStatus.Running, DeploymentStatus.Finished),
        (DeploymentStatus.Running, DeploymentStatus.Problem.Failed),
        (DeploymentStatus.Problem.Failed, DeploymentStatus.Running),
        (DeploymentStatus.Problem.Failed, DeploymentStatus.Problem.FailureDuringDeploymentRequesting),
      )
    ) { (initialStatus, toUpdatedStatus) =>
      val deploymentId = DeploymentId.generate
      deploymentRepository
        .saveDeployment(
          DeploymentEntityData(
            id = deploymentId,
            scenarioId = sampleScenarioId,
            createdAt = Timestamp.from(Instant.ofEpochMilli(0)),
            createdBy = "fooUser",
            statusWithModifiedAt = WithModifiedAt(initialStatus, Timestamp.from(Instant.ofEpochMilli(0)))
          )
        )
        .dbioActionValues
        .rightValue
      val expectedChange = toUpdatedStatus != initialStatus
      deploymentRepository
        .updateDeploymentStatus(deploymentId, toUpdatedStatus)
        .dbioActionValues shouldBe expectedChange
      val statusAfterUpdate = deploymentRepository
        .getDeploymentById(deploymentId)
        .dbioActionValues
        .value
        .deployment
        .statusWithModifiedAt
        .value
      statusAfterUpdate shouldEqual toUpdatedStatus
    }
  }

}
