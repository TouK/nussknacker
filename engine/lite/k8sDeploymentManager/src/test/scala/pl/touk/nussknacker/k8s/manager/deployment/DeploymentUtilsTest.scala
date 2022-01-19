package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.IOUtils
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.Json

class DeploymentUtilsTest extends FunSuite {

  test("should parse skuber.api.v1.Deployment with empty config"){
    val deploymentSpec = DeploymentUtils.createDeployment(ConfigFactory.empty())
    deploymentSpec.spec.get.replicas shouldBe None
  }

  test("should parse skuber.api.v1.Deployment with minimal set of fields") {
    val config = ConfigFactory.parseString(Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/deployment/deploymentMinimal.json"))).toString())
    val deploymentSpec = DeploymentUtils.createDeployment(config)
    deploymentSpec.spec.get.replicas shouldBe Some(3)
  }

  test("should parse skuber.api.v1.Deployment with full json") {
    val config = ConfigFactory.parseString(Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/deployment/deploymentFull.json"))).toString())
    val deploymentSpec = DeploymentUtils.createDeployment(config)
    deploymentSpec.spec.get.replicas shouldBe Some(1)
  }

}
