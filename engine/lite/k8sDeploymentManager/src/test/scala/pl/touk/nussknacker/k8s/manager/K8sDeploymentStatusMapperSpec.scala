package pl.touk.nussknacker.k8s.manager

import org.apache.commons.io.IOUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import play.api.libs.json.{Format, Json}
import skuber.{ListResource, Pod}
import skuber.apps.v1.Deployment
import skuber.json.format._

//It's no so easy to move deployment in unstable state reliably, so
//for now we have unit tests based on real responses - generated manually, using kubectl -v=9 describe deployment [...]
class K8sDeploymentStatusMapperSpec extends FunSuite with Matchers {

  private val mapper = new K8sDeploymentStatusMapper(K8sProcessStateDefinitionManager)

  private val timestamp = 1640769008000L

  private val version = ProcessVersion(VersionId(4), ProcessName("AAAAA"), ProcessId(7), "admin", Some(2))

  private def parseResource[T](source: String)(implicit format: Format[T]): T = {
    val value = Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/k8sResponses/$source")))
    format.reads(value).get
  }

  test("detects running scenario") {
    mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("running.json") :: Nil, Nil) shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.Running, None, Some(version), Some(timestamp), None, Nil)
    )
  }

  test("detects scenario in deployment") {
    mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("inProgress.json") :: Nil, Nil) shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.DuringDeploy, None, Some(version), Some(timestamp), None, Nil)
    )
  }


  test("detects scenario without progress") {
    mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("progressFailed.json") :: Nil, Nil) shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.Failed, None, Some(version), Some(timestamp), None,
        List("Deployment does not have minimum availability.",
          "ReplicaSet \"scenario-7-processname-aaaaa-x-5c799f64b8\" has timed out progressing.")
      )
    )
  }

  test("detects restarting (crashing) scenario") {
    mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("inProgress.json") :: Nil, parseResource[ListResource[Pod]]("podsCrashLoopBackOff.json").items) shouldBe Some(
      K8sProcessStateDefinitionManager.processState(K8sStateStatus.Restarting, None, Some(version), Some(timestamp), None, Nil)
    )
  }

  test("detects multiple deployments") {
    val deployment = parseResource[Deployment]("running.json")
    val deployment2 = deployment.copy(metadata = deployment.metadata.copy(name = "otherName"))
    mapper.findStatusForDeploymentsAndPods(deployment :: deployment2 :: Nil, Nil) shouldBe Some(
      K8sProcessStateDefinitionManager.processState(K8sStateStatus.MultipleJobsRunning, None, None, None, None,
        "Expected one deployment, instead: scenario-7-processname-aaaaa-x, otherName" :: Nil)
    )
  }

  //TODO: some test for ongoing termination??
}
