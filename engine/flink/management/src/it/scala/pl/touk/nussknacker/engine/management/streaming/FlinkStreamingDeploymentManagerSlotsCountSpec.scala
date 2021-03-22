package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, GraphProcess}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkSlotsChecker.{NotEnoughSlotsException, SlotsBalance}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

class FlinkStreamingDeploymentManagerSlotsCountSpec extends FunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: String = s"./engine/flink/management/sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementSample.jar"

  override val taskManagerSlotCount: Int = 1

  test("deploy scenario with too low task manager slots counts") {
    val processId = "processTestingTMSlots"
    val version = ProcessVersion(1, ProcessName(processId), ProcessId(12), "user1", Some(13))
    val parallelism = 2
    val process = SampleProcess.prepareProcess(processId, parallelism = Some(parallelism))

    try {
      val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
      deploymentManager.deploy(version, DeploymentData.empty, GraphProcess(marshaled), None).failed.futureValue shouldEqual
        NotEnoughSlotsException(taskManagerSlotCount, taskManagerSlotCount, SlotsBalance(0, parallelism))
    } finally {
      cancelProcess(processId)
    }
  }

}
