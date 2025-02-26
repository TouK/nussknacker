package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatusDetails, ScenarioActionName}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.util.ResourceLoader
import play.api.libs.json.{Format, Json}
import skuber.{ListResource, Pod}
import skuber.apps.v1.Deployment
import skuber.json.format._

//It's no so easy to move deployment in unstable state reliably, so
//for now we have unit tests based on real responses - generated manually, using kubectl -v=9 describe deployment [...]
class K8sDeploymentStatusMapperSpec extends AnyFunSuite with Matchers {

  private val version = VersionId(4)

  private def parseResource[T](source: String)(implicit format: Format[T]): T = {
    val value = Json.parse(ResourceLoader.load(s"/k8sResponses/$source"))
    format.reads(value).get
  }

  test("detects running scenario") {
    val state =
      K8sDeploymentStatusMapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("running.json") :: Nil, Nil)
    state shouldBe Some(
      DeploymentStatusDetails(
        status = SimpleStateStatus.Running,
        deploymentId = None,
        version = Some(version),
      )
    )
  }

  test("detects scenario in deployment") {
    val state = K8sDeploymentStatusMapper.findStatusForDeploymentsAndPods(
      parseResource[Deployment]("inProgress.json") :: Nil,
      Nil
    )
    state shouldBe Some(
      DeploymentStatusDetails(
        status = SimpleStateStatus.DuringDeploy,
        deploymentId = None,
        version = Some(version),
      )
    )
  }

  test("detects scenario without progress") {
    val state = K8sDeploymentStatusMapper.findStatusForDeploymentsAndPods(
      parseResource[Deployment]("progressFailed.json") :: Nil,
      Nil
    )
    state shouldBe Some(
      DeploymentStatusDetails(
        status = ProblemStateStatus(
          "There are some problems with scenario.",
          tooltip = Some(
            "Errors: Deployment does not have minimum availability., ReplicaSet \"scenario-7-processname-aaaaa-x-5c799f64b8\" has timed out progressing."
          )
        ),
        deploymentId = None,
        version = Some(version),
      )
    )
  }

  test("detects restarting (crashing) scenario") {
    val state = K8sDeploymentStatusMapper.findStatusForDeploymentsAndPods(
      parseResource[Deployment]("inProgress.json") :: Nil,
      parseResource[ListResource[Pod]]("podsCrashLoopBackOff.json").items
    )

    state shouldBe Some(
      DeploymentStatusDetails(
        status = SimpleStateStatus.Restarting,
        deploymentId = None,
        version = Some(version),
      )
    )
  }

  test("detects multiple deployments") {
    val deployment  = parseResource[Deployment]("running.json")
    val deployment2 = deployment.copy(metadata = deployment.metadata.copy(name = "otherName"))
    val state       = K8sDeploymentStatusMapper.findStatusForDeploymentsAndPods(deployment :: deployment2 :: Nil, Nil)

    state shouldBe Some(
      DeploymentStatusDetails(
        status = ProblemStateStatus(
          description = "More than one deployment is running.",
          allowedActions = Set(ScenarioActionName.Cancel),
          tooltip = Some("Expected one deployment, instead: scenario-7-processname-aaaaa-x, otherName")
        ),
        deploymentId = None,
        version = None,
      )
    )
  }

  // TODO: some test for ongoing termination??
}
