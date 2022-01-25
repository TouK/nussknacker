package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.management.FlinkSlotsChecker.{NotEnoughSlotsException, SlotsBalance}
import pl.touk.nussknacker.engine.marshall.ScenarioParser

class FlinkStreamingDeploymentManagerSlotsCountSpec extends FunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: List[String] = ClassPaths.scalaClasspath

  override lazy val taskManagerSlotCount: Int = 1

  test("deploy scenario with too low task manager slots counts") {
    val processId = "processTestingTMSlots"
    val version = ProcessVersion(VersionId.initialVersionId, ProcessName(processId), ProcessId(12), "user1", Some(13))
    val parallelism = 2
    val process = SampleProcess.prepareProcess(processId, parallelism = Some(parallelism))

    try {
      val processGraph = ScenarioParser.toGraphProcess(process)
      deploymentManager.deploy(version, DeploymentData.empty, processGraph, None).failed.futureValue shouldEqual
        NotEnoughSlotsException(taskManagerSlotCount, taskManagerSlotCount, SlotsBalance(0, parallelism))
    } finally {
      cancelProcess(processId)
    }
  }

}
