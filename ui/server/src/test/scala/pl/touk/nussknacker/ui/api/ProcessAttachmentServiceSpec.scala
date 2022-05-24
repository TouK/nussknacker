package pl.touk.nussknacker.ui.api

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.repository.{DbProcessActivityRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.listener.Comment

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class ProcessAttachmentServiceSpec extends FunSuite with Matchers with ScalaFutures {
  private implicit val ec = ExecutionContext.global
  private implicit val user = LoggedUser("test user", "test user")
  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  private implicit val materializer: Materializer = Materializer(actorSystem)
  private val service = new ProcessAttachmentService(AttachmentsConfig(10), TestProcessActivityRepository)

  test("should respect size limit") {
    val random12bytes = getRandomBytesSourceStream(3,4)

    val error = service.saveAttachment(ProcessId(123L), VersionId(12L), "data", random12bytes).failed.futureValue

    error.getMessage shouldBe "Maximum (10 bytes) attachment size exceeded."
  }

  test("should accept attachment with allowed size") {
    val random10bytes = getRandomBytesSourceStream(5,2)

    service.saveAttachment(ProcessId(123L), VersionId(12L), "data", random10bytes).futureValue
  }

  private def getRandomBytesSourceStream(chunks: Int, sizeOfChunk: Int) = {
    Source.apply((1 to chunks).map { _ =>
      val b = new Array[Byte](sizeOfChunk)
      new Random().nextBytes(b)
      ByteString(b)
    }.toList)
  }
}

private object TestProcessActivityRepository extends ProcessActivityRepository {
  override def addComment(processId: ProcessId, processVersionId: VersionId, comment: Comment)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = ???

  override def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit] = ???

  override def findActivity(processId: ProcessIdWithName)(implicit ec: ExecutionContext): Future[DbProcessActivityRepository.ProcessActivity] = ???

  override def addAttachment(attachmentToAdd: ProcessAttachmentService.AttachmentToAdd)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = Future.successful(())

  override def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]] = ???
}