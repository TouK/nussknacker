package pl.touk.nussknacker.engine.management.streaming

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, GraphProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import scala.concurrent.duration._

class JavaConfigDeploymentManagerSpec extends FunSuite with Matchers with StreamingDockerTest {

  override protected def classPath: List[String] = ClassPaths.javaClasspath

  test("deploy java scenario in running flink") {
    val processId = "runningJavaFlink"

    val process = EspProcessBuilder
          .id(processId)
          .source("startProcess", "source")
          .emptySink("endSend", "sink")

    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(deploymentManager.deploy(ProcessVersion.empty.copy(processName=ProcessName(process.id)), DeploymentData.empty,
      GraphProcess(marshaled), None).isReadyWithin(100 seconds))

    eventually {
      val jobStatus = deploymentManager.findJobStatus(ProcessName(process.id)).futureValue
      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }

    assert(deploymentManager.cancel(ProcessName(process.id), user = userToAct).isReadyWithin(10 seconds))
  }
}
