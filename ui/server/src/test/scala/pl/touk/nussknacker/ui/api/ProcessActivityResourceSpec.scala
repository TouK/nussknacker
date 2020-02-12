package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.apache.commons.io.FileUtils
import org.scalatest._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.util.{DateUtils, MultipartUtils}

import scala.language.higherKinds

class ProcessActivityResourceSpec extends FlatSpec with ScalatestRouteTest with Matchers with PatientScalaFutures with BeforeAndAfterEach with EspItTest with FailFastCirceSupport {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  val processActivityRouteWithAllPermission: Route = withAllPermissions(processActivityRoute)

  val attachmentsRouteWithPermissions: Route = withAllPermissions(attachmentsRoute)

  it should "add and remove comment in process activity" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val commentContent = "test message"
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK}
    Post(s"/processes/${processToSave.id}/1/activity/comments", HttpEntity(ContentTypes.`text/plain(UTF-8)`, commentContent)) ~> processActivityRouteWithAllPermission ~> check {
      status shouldEqual StatusCodes.OK
      Get(s"/processes/${processToSave.id}/activity") ~> processActivityRouteWithAllPermission ~> check {
        val processActivity = responseAs[ProcessActivity]
        val firstComment = processActivity.comments.head
        processActivity.comments should have size 1
        processActivity.comments.head.content shouldBe commentContent
        Delete(s"/processes/${processToSave.id}/activity/comments/${firstComment.id}") ~> processActivityRouteWithAllPermission ~> check {
          status shouldEqual StatusCodes.OK
          Get(s"/processes/${processToSave.id}/activity") ~> processActivityRouteWithAllPermission ~> check {
            val newProcessActivity = responseAs[ProcessActivity]
            newProcessActivity.comments shouldBe empty
          }
        }
      }
    }
  }

  it should "add attachment to process and then be able to download it" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK}

    val fileName = "important_file.txt"
    val fileContent = "very important content"
    val mutipartFile = MultipartUtils.prepareMultiPart(fileContent, "attachment", fileName)

    Post(s"/processes/${processToSave.id}/1/activity/attachments", mutipartFile) ~> attachmentsRouteWithPermissions ~> check {
      status shouldEqual StatusCodes.OK

      Get(s"/processes/${processToSave.id}/activity") ~> processActivityRouteWithAllPermission ~> check {
        val processActivity = responseAs[ProcessActivity]
        val attachment = processActivity.attachments.head
        attachment.fileName shouldBe fileName
        attachment.processId shouldBe processToSave.id
        getAttachment(processToSave.id, attachment.id) { responseAs[String] shouldBe fileContent }
      }
    }
  }

  it should "handle attachments with the same name" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK}

    val fileName = "important_file.txt"
    val fileContent1 = "very important content1"
    val fileContent2 = "very important content2"
    val mutipartFile1 = MultipartUtils.prepareMultiPart(fileContent1, "attachment", fileName)
    val mutipartFile2 = MultipartUtils.prepareMultiPart(fileContent2, "attachment", fileName)

    Post(s"/processes/${processToSave.id}/1/activity/attachments", mutipartFile1) ~> attachmentsRouteWithPermissions ~> check {
      status shouldEqual StatusCodes.OK
      Post(s"/processes/${processToSave.id}/1/activity/attachments", mutipartFile2) ~> attachmentsRouteWithPermissions ~> check {
        status shouldEqual StatusCodes.OK
        Get(s"/processes/${processToSave.id}/activity") ~> processActivityRouteWithAllPermission ~> check {
          val processActivity = responseAs[ProcessActivity]
          processActivity.attachments.size shouldBe 2
          val attachmentsOrdered = processActivity.attachments.sortBy(a => DateUtils.toMillis(a.createDate))
          val (attachment1, attachment2) = (attachmentsOrdered(0), attachmentsOrdered(1))
          getAttachment(processToSave.id, attachment1.id) { responseAs[String] shouldBe fileContent1 }
          getAttachment(processToSave.id, attachment2.id) { responseAs[String] shouldBe fileContent2 }
        }
      }
    }
  }

  def getAttachment(processId: String, attachmentId: Long)(testCode: => Assertion) = {
    Get(s"/processes/${processId}/1/activity/attachments/${attachmentId}") ~> attachmentsRouteWithPermissions ~> check {
      testCode
    }
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    FileUtils.deleteDirectory(new java.io.File(attachmentsPath))
  }
}
