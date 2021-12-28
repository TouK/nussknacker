package pl.touk.nussknacker.k8s.manager

import org.apache.commons.io.IOUtils
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import play.api.libs.json.{Format, Json}
import skuber.apps.v1.Deployment

//It's no so easy to move deployment in unstable state reliably, so
//for now we have unit tests based on real responses - generated manually, using kubectl -v=9 describe deployment [...]
class K8sDeploymentStatusMapperSpec extends FunSuite with Matchers {

  private val mapper = new K8sDeploymentStatusMapper(K8sProcessStateDefinitionManager)

  private val timestamp = 1640769008000L

  private val version = ProcessVersion(VersionId(4), ProcessName("AAAAA"), ProcessId(7), "admin", Some(2))

  private def parseResource(source: String): Deployment = {
    val format = implicitly[Format[Deployment]]
    val value = Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/k8sResponses/$source")))
    format.reads(value).get
  }

  test("detects running scenario") {
    mapper.findStatusForDeployments(parseResource("running.json") :: Nil) shouldBe Some(
      ProcessState(None, SimpleStateStatus.Running, Some(version), K8sProcessStateDefinitionManager, Some(timestamp), None, Nil)
    )
  }

  test("detects scenario in deployment") {
    mapper.findStatusForDeployments(parseResource("inProgress.json") :: Nil) shouldBe Some(
      ProcessState(None, SimpleStateStatus.DuringDeploy, Some(version), K8sProcessStateDefinitionManager, Some(timestamp), None, Nil)
    )
  }


  test("detects scenario without progress") {
    mapper.findStatusForDeployments(parseResource("progressFailed.json") :: Nil) shouldBe Some(
      ProcessState(None, SimpleStateStatus.Failed, Some(version), K8sProcessStateDefinitionManager, Some(timestamp), None,
        List("Deployment does not have minimum availability.",
          "ReplicaSet \"scenario-7-processname-aaaaa-x-5c799f64b8\" has timed out progressing.")
      )
    )
  }

  test("detects multiple deployments") {
    val deployment = parseResource("running.json")
    val deployment2 = deployment.copy(metadata = deployment.metadata.copy(name = "otherName"))
    mapper.findStatusForDeployments(deployment :: deployment2 :: Nil) shouldBe Some(
      ProcessState(None, K8sStateStatus.MultipleJobsRunning, None, K8sProcessStateDefinitionManager, None, None,
        "Expected one deployment, instead: scenario-7-processname-aaaaa-x, otherName" :: Nil)
    )
  }

  //TODO: some test for ongoing termination??
}
