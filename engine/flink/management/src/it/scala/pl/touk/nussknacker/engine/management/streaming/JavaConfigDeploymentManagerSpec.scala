package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentUpdateStrategy,
  DMCancelScenarioCommand,
  DMRunDeploymentCommand
}
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData

import scala.concurrent.duration._

class JavaConfigDeploymentManagerSpec extends AnyFunSuite with Matchers with FlinkKafkaDockerSpec with StrictLogging {

  override protected def useMiniClusterForDeployment: Boolean = false

  override protected def modelClassPath: List[String] = TestModelClassPaths.javaClasspath

  test("deploy java scenario in running flink") {
    val processId = "runningJavaFlink"

    val process = ScenarioBuilder
      .streaming(processId)
      .source("startProcess", "source")
      .emptySink("endSend", "sink")

    val versionId      = VersionId(4)
    val processVersion = ProcessVersion.empty.copy(processName = process.name, versionId = versionId)

    assert(
      deploymentManager
        .processCommand(
          DMRunDeploymentCommand(
            processVersion,
            DeploymentData.empty,
            process,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .isReadyWithin(100 seconds)
    )

    eventually {
      val jobStatus = deploymentManager.getScenarioDeploymentsStatuses(process.name).futureValue.value
      jobStatus.map(_.status) should matchPattern { case List(SimpleStateStatus.Running(`versionId`, _)) =>
      }
    }

    assert(
      deploymentManager
        .processCommand(DMCancelScenarioCommand(process.name, user = userToAct))
        .isReadyWithin(10 seconds)
    )
  }

}
