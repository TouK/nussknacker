package pl.touk.nussknacker.ui.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.repository.{DbProcessActivityRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.Random

class ScenarioAttachmentServiceSpec extends AnyFunSuite with Matchers with ScalaFutures {
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  private implicit val user: LoggedUser             = LoggedUser("test user", "test user")
  private val service = new ScenarioAttachmentService(AttachmentsConfig(10), TestProcessActivityRepository)

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

private object TestProcessActivityRepository extends ProcessActivityRepository {

  override def addComment(processId: ProcessId, processVersionId: VersionId, comment: Comment)(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Unit] = ???

  override def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit] = ???

  override def findActivity(processId: ProcessId)(
      implicit ec: ExecutionContext
  ): Future[DbProcessActivityRepository.ProcessActivity] = ???

  override def addAttachment(
      attachmentToAdd: ScenarioAttachmentService.AttachmentToAdd
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = Future.successful(())

  override def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]] =
    ???
}
