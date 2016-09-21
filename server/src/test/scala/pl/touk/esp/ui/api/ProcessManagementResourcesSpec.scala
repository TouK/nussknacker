package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import db.migration.DefaultJdbcProfile
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.ui.api.helpers.{DbTesting, InMemoryMocks}
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.sample.SampleProcess

class ProcessManagementResourcesSpec extends FlatSpec with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues {

  val db = DbTesting.db
  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile)
  val deploymentProcessRepository = new DeployedProcessRepository(db, DefaultJdbcProfile.profile)
  val mockProcessManager = InMemoryMocks.mockProcessManager
  val route = new ManagementResources(processRepository, deploymentProcessRepository, mockProcessManager).route

  it should "save deployed process" in {
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      whenReady(deploymentProcessRepository.fetchDeployedProcessById(SampleProcess.process.id)) { deployedProcess =>
        deployedProcess.value.id shouldBe SampleProcess.process.id
      }
    }
  }
}
