package pl.touk.esp.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.Argonaut._
import argonaut.{DecodeJson, EncodeJson}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.api.helpers.{DbTesting, TestFactory}
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessTypeCodec}
import pl.touk.esp.ui.process.repository.ProcessRepository.{DeploymentEntry, ProcessDetails}
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.security.Permission

class ProcessManagementResourcesSpec extends FlatSpec with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues {

  import argonaut.ArgonautShapeless._
  implicit val decoder = DisplayableProcessCodec.codec
  implicit val processTypeCodec = ProcessTypeCodec.codec
  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))
  implicit val processListEncode = DecodeJson.of[ProcessDetails]
  val db = DbTesting.db
  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  val processRepository = newProcessRepository(db)
  val deploymentProcessRepository = newDeploymentProcessRepository(db)

  val deployRoute = new ManagementResources(processRepository, deploymentProcessRepository,
    InMemoryMocks.mockProcessManager, "test").route
  val processesRoute = new ProcessesResources(processRepository, InMemoryMocks.mockProcessManager,
    processConverter, processValidation).route

  it should "process deployment should be visible in process history" in {
    Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, Permission.Read) ~> check {
      val oldDeployments = getHistoryDeployments
      Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute, Permission.Deploy) ~> check {
        Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, Permission.Read) ~> check {
          val currentDeployments = getHistoryDeployments
          currentDeployments.size shouldBe oldDeployments.size
          oldDeployments.size shouldBe 1
          currentDeployments.head.environment shouldBe oldDeployments.head.environment
          currentDeployments.head.deployedAt should not be oldDeployments.head.deployedAt
        }
      }
    }
  }

  it should "not authorize user with write permission to deploy" in {
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute, Permission.Write) ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }
  }

  private def getHistoryDeployments = responseAs[String].decodeOption[ProcessDetails].get.history.flatMap(_.deployments)
}
