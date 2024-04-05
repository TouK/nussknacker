package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CancelScenarioCommand, RunDeploymentCommand}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData

import scala.concurrent.duration._

class JavaConfigDeploymentManagerSpec extends AnyFunSuite with Matchers with StreamingDockerTest with LazyLogging {

  override protected def classPath: List[String] = ClassPaths.javaClasspath

  test("deploy java scenario in running flink") {
    val processId = "runningJavaFlink"

    val process = ScenarioBuilder
      .streaming(processId)
      .source("startProcess", "source")
      .emptySink("endSend", "sink")

    assert(
      deploymentManager
        .processCommand(
          RunDeploymentCommand(
            ProcessVersion.empty.copy(processName = process.name),
            DeploymentData.empty,
            process,
            None
          )
        )
        .isReadyWithin(100 seconds)
    )

    eventually {
      val jobStatus = deploymentManager.getProcessStates(process.name).futureValue.value
      jobStatus.map(_.status) shouldBe List(SimpleStateStatus.Running)
    }

    assert(
      deploymentManager.processCommand(CancelScenarioCommand(process.name, user = userToAct)).isReadyWithin(10 seconds)
    )
  }

}
