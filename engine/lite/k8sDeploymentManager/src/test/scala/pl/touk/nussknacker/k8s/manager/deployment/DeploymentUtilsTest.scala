package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper

class DeploymentUtilsTest extends FunSuite {

  test("should parse skuber.api.v1.Deployment with empty config"){
    val deploymentSpec = DeploymentUtils.parseDeploymentWithFallback(ConfigFactory.empty())
    deploymentSpec.spec.get.replicas shouldBe None
  }

  test("should parse skuber.api.v1.Deployment with minimal set of fields") {
    val config = ConfigFactory.parseURL(getClass.getResource(s"/deployment/deploymentMinimal.json"))
    val deploymentSpec = DeploymentUtils.parseDeploymentWithFallback(config)
    deploymentSpec.spec.get.replicas shouldBe Some(3)
  }

  test("should parse skuber.api.v1.Deployment with full json") {
    val config = ConfigFactory.parseURL(getClass.getResource(s"/deployment/deploymentFull.json"))
    val deploymentSpec = DeploymentUtils.parseDeploymentWithFallback(config)
    deploymentSpec.spec.get.replicas shouldBe Some(1)
  }

}
