package pl.touk.nussknacker.ui.process

import db.util.DBIOActionInstances.DB
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityId}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
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

  override def addActivity(scenarioActivity: ScenarioActivity)(implicit user: LoggedUser): DB[ScenarioActivityId] = ???

  override def addComment(scenarioId: ProcessId, processVersionId: VersionId, comment: String)(
      implicit user: LoggedUser
  ): DB[ScenarioActivityId] = ???

  override def editComment(scenarioActivityId: ScenarioActivityId, comment: String)(
      implicit user: LoggedUser
  ): DB[Either[ScenarioActivityRepository.ModifyCommentError, Unit]] = ???

  override def deleteComment(scenarioActivityId: ScenarioActivityId)(
      implicit user: LoggedUser
  ): DB[Either[ScenarioActivityRepository.ModifyCommentError, Unit]] = ???

  override def addAttachment(attachmentToAdd: ScenarioAttachmentService.AttachmentToAdd)(
      implicit user: LoggedUser
  ): DB[ScenarioActivityId] =
    DBIO.successful(ScenarioActivityId.random)

  override def findAttachment(scenarioId: ProcessId, attachmentId: Long): DB[Option[AttachmentEntityData]] = ???

  override def findActivity(processId: ProcessId): DB[String] = ???

  override def getActivityStats: DB[Map[String, Int]] = ???

}
