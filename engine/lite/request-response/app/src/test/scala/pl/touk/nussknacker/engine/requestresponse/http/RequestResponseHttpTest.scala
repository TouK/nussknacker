package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.Encoder
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.requestresponse.deployment.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.requestresponse.http.logging.RequestResponseLogger
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import pl.touk.nussknacker.engine.testing.ModelJarBuilder

import java.nio.file.Files
import java.util
import java.util.UUID

class RequestResponseHttpTest extends AnyFlatSpec with Matchers with ScalatestRouteTest with FailFastCirceSupport with BeforeAndAfterEach {

  private val config = ConfigFactory.load()
    .withValue("scenarioRepositoryLocation", fromAnyRef(Files.createTempDirectory("scenarioLocation").toFile.getAbsolutePath))
    .withValue("modelConfig.classPath", fromIterable(
      util.Arrays.asList(ModelJarBuilder.buildJarWithConfigCreator[TestConfigCreator]().getAbsolutePath))
    )
  val exampleApp = new RequestResponseHttpApp(config, new MetricRegistry)
  val managementRoute: Route = exampleApp.managementRoute.route
  val processesRoute: Route = exampleApp.processRoute.route(RequestResponseLogger.default)

  var procId : ProcessName = _
  override protected def beforeEach(): Unit = {
    procId = ProcessName(UUID.randomUUID().toString)
  }

  val testEpoch: Long = (math.random * 10000).toLong

  def deploymentData(canonicalProcess: CanonicalProcess): RequestResponseDeploymentData =
    RequestResponseDeploymentData(canonicalProcess, testEpoch, ProcessVersion.empty.copy(processName=procId), DeploymentData.empty)

  def cancelProcess(processName: ProcessName): Assertion = {
    val id = processName.value

    Post(s"/cancel/$id") ~> managementRoute ~> check {
      status shouldBe StatusCodes.OK
      assertProcessNotRunning(processName)
    }
  }

  def assertProcessNotRunning(processName: ProcessName): Assertion = {
    val id = processName.value
    Get(s"/checkStatus/$id") ~> managementRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  def toEntity[J : Encoder](jsonable: J): RequestEntity = {
    val json = jsonable.asJson.spaces2
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def stringAsJsonEntity(jsonMessage: String): RequestEntity = {
    HttpEntity(ContentTypes.`application/json`, jsonMessage)
  }

}
