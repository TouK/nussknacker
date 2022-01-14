package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import org.apache.commons.io.IOUtils
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.Json

class DeploymentSpecUtilsTest extends FunSuite {

  test("should parse skuber.api.Deployment.Spec with only one field") {
    val config = ConfigFactory.empty()
      .withValue("replicas", fromAnyRef(3))
    val deploymentSpec = DeploymentSpecUtils.createDeploymentSpec(config)
    deploymentSpec.replicas shouldBe Some(3)
  }

  test("should parse skuber.api.v1.Deployment.Spec with only one field") {
    val config = ConfigFactory.empty()
      .withValue("replicas", fromAnyRef(3))
    val deploymentSpec = DeploymentSpecUtils.createDeploymentSpecV1(config)
    deploymentSpec.replicas shouldBe Some(3)
  }

  test("should parse skuber.api.v1.Deployment.Spec with full json") {
    val config = ConfigFactory.parseString(Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/deployment/deploymentSpec1.json"))).toString())
    val deploymentSpec = DeploymentSpecUtils.createDeploymentSpecV1(config)
    deploymentSpec.replicas shouldBe Some(1)
  }

  test("should merge two deployment specs"){
    val config1 = ConfigFactory.parseString(Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/deployment/deploymentSpec1.json"))).toString())
    val deploymentSpec1 = DeploymentSpecUtils.createDeploymentSpecV1(config1)

    val config2 = ConfigFactory.parseString(Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/deployment/deploymentSpec2.json"))).toString())
    val deploymentSpec2 = DeploymentSpecUtils.createDeploymentSpecV1(config2)

    val mergedDeploymentSpec = DeploymentSpecUtils.mergeDeploymentSpec(Option(deploymentSpec1), deploymentSpec2)
    mergedDeploymentSpec.replicas shouldBe Some(2)
    mergedDeploymentSpec.selector.toString() shouldBe "nussknacker.io/label2=1234,nussknacker.io/label1=1234,nussknacker.io/scenarioId=1234"
  }

}
