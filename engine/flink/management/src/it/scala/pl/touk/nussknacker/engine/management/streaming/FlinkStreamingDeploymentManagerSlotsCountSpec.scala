package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, GraphProcess, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import scala.concurrent.duration._

class FlinkStreamingDeploymentManagerSlotsCountSpec extends FunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: String = s"./engine/flink/management/sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementSample.jar"

  override val taskManagerSlotCount: Int = 1

  // manual test because it takes a while to verify that
  ignore("deploy scenario with too low task manager slots counts") {
    val processId = "processTestingTMSlots"
    val version = ProcessVersion(1, ProcessName(processId), "user1", Some(13))
    val process = SampleProcess.prepareProcess(processId, parallelism = Some(2))

    try {
      deployProcess(process, version)
      continuouslyHaveStateStatus(process.id, FlinkStateStatus.DuringDeploy, 10, 1 second)
    } finally {
      cancelProcess(processId)
    }
  }

  private def continuouslyHaveStateStatus(processId: String, expectedStatus: StateStatus, attempts: Int, checkInterval: FiniteDuration): Unit = {
    (0 until attempts).foreach { attempt =>
      val jobStatus = deploymentManager.findJobStatus(ProcessName(processId)).futureValue
      logger.debug(s"Checking if: $processId, have expected state status: $expectedStatus, current job status: $jobStatus, attempt: $attempt")

      jobStatus.map(_.status.name) shouldBe Some(expectedStatus.name)
      if (attempt != attempts - 1) {
        Thread.sleep(checkInterval.toMillis)
      }
    }
  }
}
