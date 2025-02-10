package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.{DMRunDeploymentCommand, DeploymentUpdateStrategy}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.FlinkSlotsChecker.{NotEnoughSlotsException, SlotsBalance}

class FlinkStreamingDeploymentManagerSlotsCountSpec
    extends AnyFunSuite
    with Matchers
    with StreamingDockerTest
    with StrictLogging {

  override protected def useMiniClusterForDeployment: Boolean = false

  override protected def classPath: List[String] = ClassPaths.scalaClasspath

  override lazy val taskManagerSlotCount: Int = 1

  test("deploy scenario with too low task manager slots counts") {
    val processName = ProcessName("processTestingTMSlots")
    val version = ProcessVersion(VersionId.initialVersionId, processName, ProcessId(12), List.empty, "user1", Some(13))
    val parallelism = 2
    val process     = SampleProcess.prepareProcess(processName, parallelism = Some(parallelism))

    try {
      deploymentManager
        .processCommand(
          DMRunDeploymentCommand(
            version,
            DeploymentData.empty,
            process,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .failed
        .futureValue shouldEqual
        NotEnoughSlotsException(taskManagerSlotCount, taskManagerSlotCount, SlotsBalance(0, parallelism))
    } finally {
      cancelProcess(processName)
    }
  }

}
