package pl.touk.nussknacker.k8s.manager

import org.apache.commons.io.IOUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.util.ResourceLoader
import play.api.libs.json.{Format, Json}
import skuber.{ListResource, Pod}
import skuber.apps.v1.Deployment
import skuber.json.format._

//It's no so easy to move deployment in unstable state reliably, so
//for now we have unit tests based on real responses - generated manually, using kubectl -v=9 describe deployment [...]
class K8sDeploymentStatusMapperSpec extends AnyFunSuite with Matchers {

  private val mapper = new K8sDeploymentStatusMapper(K8sProcessStateDefinitionManager)

  private val timestamp = 1640769008000L

  private val version = ProcessVersion(VersionId(4), ProcessName("AAAAA"), ProcessId(7), "admin", Some(2))

  private def parseResource[T](source: String)(implicit format: Format[T]): T = {
    val value = Json.parse(ResourceLoader.load(s"/k8sResponses/$source"))
    format.reads(value).get
  }

  test("detects running scenario") {
    val state = mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("running.json") :: Nil, Nil)
    state shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.Running, None, Some(version), Some(timestamp), None, Nil)
    )
    state.toList.flatMap(_.allowedActions) should contain theSameElementsAs List(ProcessActionType.Deploy, ProcessActionType.Pause, ProcessActionType.Cancel)
  }

  test("detects scenario in deployment") {
    val state = mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("inProgress.json") :: Nil, Nil)
    state shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.DuringDeploy, None, Some(version), Some(timestamp), None, Nil)
    )
    state.toList.flatMap(_.allowedActions) should contain theSameElementsAs List(ProcessActionType.Deploy, ProcessActionType.Cancel)
  }


  test("detects scenario without progress") {
    val state = mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("progressFailed.json") :: Nil, Nil)
    state shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.Failed, None, Some(version), Some(timestamp), None,
        List("Deployment does not have minimum availability.",
          "ReplicaSet \"scenario-7-processname-aaaaa-x-5c799f64b8\" has timed out progressing.")
      )
    )
    state.toList.flatMap(_.allowedActions) should contain theSameElementsAs List(ProcessActionType.Deploy, ProcessActionType.Cancel)
  }

  test("detects restarting (crashing) scenario") {
    val state = mapper.findStatusForDeploymentsAndPods(parseResource[Deployment]("inProgress.json") :: Nil, parseResource[ListResource[Pod]]("podsCrashLoopBackOff.json").items)

    state shouldBe Some(
      K8sProcessStateDefinitionManager.processState(SimpleStateStatus.Restarting, None, Some(version), Some(timestamp), None, Nil)
    )
    state.toList.flatMap(_.allowedActions) should contain theSameElementsAs List(ProcessActionType.Deploy, ProcessActionType.Cancel)
  }

  test("detects multiple deployments") {
    val deployment = parseResource[Deployment]("running.json")
    val deployment2 = deployment.copy(metadata = deployment.metadata.copy(name = "otherName"))
    val state = mapper.findStatusForDeploymentsAndPods(deployment :: deployment2 :: Nil, Nil)

    state shouldBe Some(
      K8sProcessStateDefinitionManager.processState(K8sStateStatus.MultipleJobsRunning, None, None, None, None,
        "Expected one deployment, instead: scenario-7-processname-aaaaa-x, otherName" :: Nil)
    )
    state.toList.flatMap(_.allowedActions) should contain theSameElementsAs List(ProcessActionType.Cancel)
  }

  //TODO: some test for ongoing termination??
}
