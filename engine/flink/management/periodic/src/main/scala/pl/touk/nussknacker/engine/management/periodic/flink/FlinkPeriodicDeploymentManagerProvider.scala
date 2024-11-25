package pl.touk.nussknacker.engine.management.periodic.flink

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.common.periodic.PeriodicDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}

import scala.concurrent.duration.FiniteDuration

class FlinkPeriodicDeploymentManagerProvider
    extends PeriodicDeploymentManagerProvider(
      name = "flinkPeriodic",
      delegate = new FlinkStreamingDeploymentManagerProvider(),
      periodicDeploymentService = new FlinkPeriodicDeploymentService(
        flinkClient = ???,
        jarsDir = ???,
        inputConfigDuringExecution = ???,
        modelJarProvider = ???
      ),
    )
