package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, BeforeAndAfterEach}
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withAllPermissions
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, ProcessTestData}
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.util.MultipartUtils

import scala.language.higherKinds

class ProcessActivityResourceSpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with PatientScalaFutures
    with BeforeAndAfterEach
    with NuResourcesTest
    with FailFastCirceSupport {

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val attachmentService = new ProcessAttachmentService(AttachmentsConfig.default, processActivityRepository)
  private val attachmentsRoute =
    new AttachmentResources(attachmentService, processService, processAuthorizer)
  private val attachmentsRouteWithAllPermissions: Route = withAllPermissions(attachmentsRoute)

  private val process: DisplayableProcess = ProcessTestData.sampleDisplayableProcess

  it should "add and remove comment in process activity" in {
    val processToSave  = process
    val commentContent = "test message"
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK }
    Post(
      s"/processes/${processToSave.name}/1/activity/comments",
      HttpEntity(ContentTypes.`text/plain(UTF-8)`, commentContent)
    ) ~> processActivityRouteWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      getActivity(processToSave.name) ~> check {
        val processActivity = responseAs[ProcessActivity]
        val firstComment    = processActivity.comments.head
        processActivity.comments should have size 1
        processActivity.comments.head.content shouldBe commentContent
        Delete(
          s"/processes/${processToSave.name}/activity/comments/${firstComment.id}"
        ) ~> processActivityRouteWithAllPermissions ~> check {
          status shouldEqual StatusCodes.OK
          getActivity(processToSave.name) ~> check {
            val newProcessActivity = responseAs[ProcessActivity]
            newProcessActivity.comments shouldBe empty
          }
        }
      }
    }
  }

  it should "add attachment to process and then be able to download it" in {
    val processToSave = process
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK }

    val fileName     = "important_file.txt"
    val fileContent  = "very important content"
    val mutipartFile = MultipartUtils.prepareMultiPart(fileContent, "attachment", fileName)

    Post(
      s"/processes/${processToSave.name}/1/activity/attachments",
      mutipartFile
    ) ~> attachmentsRouteWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK

      getActivity(processToSave.name) ~> check {
        val processActivity = responseAs[ProcessActivity]
        val attachment      = processActivity.attachments.head
        attachment.fileName shouldBe fileName
        attachment.processId shouldBe processToSave.name
        getAttachment(processToSave.name, attachment.id) { responseAs[String] shouldBe fileContent }
      }
    }
  }

  it should "handle attachments with the same name" in {
    val processToSave = process
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK }

    val fileName      = "important_file.txt"
    val fileContent1  = "very important content1"
    val fileContent2  = "very important content2"
    val mutipartFile1 = MultipartUtils.prepareMultiPart(fileContent1, "attachment", fileName)
    val mutipartFile2 = MultipartUtils.prepareMultiPart(fileContent2, "attachment", fileName)

    Post(
      s"/processes/${processToSave.name}/1/activity/attachments",
      mutipartFile1
    ) ~> attachmentsRouteWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      Post(
        s"/processes/${processToSave.name}/1/activity/attachments",
        mutipartFile2
      ) ~> attachmentsRouteWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        getActivity(processToSave.name) ~> check {
          val processActivity = responseAs[ProcessActivity]
          processActivity.attachments.size shouldBe 2
          val attachmentsOrdered         = processActivity.attachments.sortBy(_.createDate)
          val (attachment1, attachment2) = (attachmentsOrdered.head, attachmentsOrdered(1))
          getAttachment(processToSave.name, attachment1.id) { responseAs[String] shouldBe fileContent1 }
          getAttachment(processToSave.name, attachment2.id) { responseAs[String] shouldBe fileContent2 }
        }
      }
    }
  }

  private def getAttachment(processName: ProcessName, attachmentId: Long)(testCode: => Assertion) = {
    Get(
      s"/processes/$processName/1/activity/attachments/$attachmentId"
    ) ~> attachmentsRouteWithAllPermissions ~> check {
      testCode
    }
  }

}
