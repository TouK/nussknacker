package pl.touk.nussknacker.engine.management

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

/**
  * FlinkConfig deployment configuration.
  *
  * @param restUrl REST API endpoint of the Flink cluster.
  * @param jobManagerTimeout Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.
  * @param shouldVerifyBeforeDeploy By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.
  * @param shouldCheckAvailableSlots When true {@link FlinkDeploymentManager} checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.
  */
case class FlinkConfig(restUrl: String,
                       jobManagerTimeout: FiniteDuration = 1 minute,
                       shouldVerifyBeforeDeploy: Boolean = true,
                       shouldCheckAvailableSlots: Boolean = true,
                       waitForDuringDeployFinish: Option[FlinkWaitForDuringDeployFinishedConfig] =
                       Some(FlinkWaitForDuringDeployFinishedConfig(60, 1 second)))

case class FlinkWaitForDuringDeployFinishedConfig(maxChecks: Int, delay: FiniteDuration)
