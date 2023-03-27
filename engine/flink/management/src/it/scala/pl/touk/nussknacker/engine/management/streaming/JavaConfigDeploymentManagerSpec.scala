package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.FlinkStateStatus

import scala.concurrent.duration._

class JavaConfigDeploymentManagerSpec extends AnyFunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: List[String] = ClassPaths.javaClasspath

  test("deploy java scenario in running flink") {
    val processId = "runningJavaFlink"

    val process = ScenarioBuilder
          .streaming(processId)
          .source("startProcess", "source")
          .emptySink("endSend", "sink")

    assert(deploymentManager.deploy(ProcessVersion.empty.copy(processName=ProcessName(process.id)), DeploymentData.empty,
      process, None).isReadyWithin(100 seconds))

    eventually {
      val jobStatus = deploymentManager.getProcessState(ProcessName(process.id)).futureValue.value
      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }

    assert(deploymentManager.cancel(ProcessName(process.id), user = userToAct).isReadyWithin(10 seconds))
  }
}
