package pl.touk.nussknacker.engine.management

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

/**
  * FlinkConfig deployment configuration.
  *
  * @param restUrl REST API endpoint of the Flink cluster.
  * @param queryableStateProxyUrl Some Nussknacker extensions require access to Flink queryable state. This should be comma separated list of host:port addresses of queryable state proxies of all Taskmanagers in the cluster.
  * @param jobManagerTimeout Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.
  * @param shouldVerifyBeforeDeploy By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.
  * @param shouldCheckAvailableSlots When true {@link FlinkDeploymentManager} checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.
  */
case class FlinkConfig(restUrl: String,
                       queryableStateProxyUrl: Option[String],
                       jobManagerTimeout: FiniteDuration = 1 minute,
                       shouldVerifyBeforeDeploy: Boolean = true,
                       shouldCheckAvailableSlots: Boolean = true)
