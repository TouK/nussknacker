package pl.touk.nussknacker.ui.process

import db.util.DBIOActionInstances.DB
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityId}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy.ProcessActivity
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.ModifyActivityError
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}
import slick.dbio.DBIO

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.Random

class ScenarioAttachmentServiceSpec extends AnyFunSuite with Matchers with ScalaFutures {
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  private implicit val user: LoggedUser             = RealLoggedUser("test user", "test user")

  private val service = new ScenarioAttachmentService(
    AttachmentsConfig(10),
    TestProcessActivityRepository,
    TestFactory.newDummyDBIOActionRunner()
  )

  test("should respect size limit") {
    val random12bytes = new ByteArrayInputStream(nextBytes(12))

    val error = service.saveAttachment(ProcessId(123L), VersionId(12L), "data", random12bytes).failed.futureValue

    error.getMessage shouldBe "Maximum (10 bytes) attachment size exceeded."
  }

  test("should accept attachment with allowed size") {
    val random10bytes = new ByteArrayInputStream(nextBytes(10))

    service.saveAttachment(ProcessId(123L), VersionId(12L), "data", random10bytes).futureValue
  }

  private def nextBytes(length: Int): Array[Byte] = {
    val b = new Array[Byte](length)
    new Random().nextBytes(b)
    b
  }

}

private object TestProcessActivityRepository extends ScenarioActivityRepository {

  override def findActivities(scenarioId: ProcessId): DB[Seq[ScenarioActivity]] = notSupported("findActivities")

  override def addActivity(scenarioActivity: ScenarioActivity): DB[ScenarioActivityId] = notSupported("addActivity")

  override def addComment(scenarioId: ProcessId, processVersionId: VersionId, comment: String)(
      implicit user: LoggedUser
  ): DB[ScenarioActivityId] = notSupported("addComment")

  override def addAttachment(attachmentToAdd: ScenarioAttachmentService.AttachmentToAdd)(
      implicit user: LoggedUser
  ): DB[ScenarioActivityId] =
    DBIO.successful(ScenarioActivityId.random)

  override def findAttachments(scenarioId: ProcessId): DB[Seq[AttachmentEntityData]] = notSupported("findAttachments")

  override def findAttachment(scenarioId: ProcessId, attachmentId: Long): DB[Option[AttachmentEntityData]] =
    notSupported("findAttachment")

  override def findActivity(processId: ProcessId): DB[ProcessActivity] = notSupported("findActivity")

  override def getActivityStats: DB[Map[String, Int]] = notSupported("getActivityStats")

  override def editComment(scenarioId: ProcessId, scenarioActivityId: ScenarioActivityId, comment: String)(
      implicit user: LoggedUser
  ): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = notSupported("editComment")

  override def editComment(scenarioId: ProcessId, commentId: Long, comment: String)(
      implicit user: LoggedUser
  ): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = notSupported("editComment")

  override def deleteComment(scenarioId: ProcessId, commentId: Long)(
      implicit user: LoggedUser
  ): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = notSupported("deleteComment")

  override def deleteComment(scenarioId: ProcessId, scenarioActivityId: ScenarioActivityId)(
      implicit user: LoggedUser
  ): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = notSupported("deleteComment")

  private def notSupported(methodName: String): Nothing = throw new Exception(
    s"Method $methodName not supported by TestProcessActivityRepository test implementation"
  )

}
