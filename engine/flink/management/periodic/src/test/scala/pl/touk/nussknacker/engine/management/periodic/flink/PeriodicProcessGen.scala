package pl.touk.nussknacker.engine.management.periodic.flink

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager.CronScheduleProperty
import pl.touk.nussknacker.engine.api.deployment.periodic.model.DeploymentWithRuntimeParams.WithConfig
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName

import java.time.LocalDateTime

object PeriodicProcessGen {

  def apply(): PeriodicProcess[WithConfig] = {
    PeriodicProcess(
      id = PeriodicProcessId(42),
      deploymentData = DeploymentWithRuntimeParams.WithConfig(
        processVersion = ProcessVersion.empty,
        inputConfigDuringExecutionJson = "{}",
        runtimeParams = RuntimeParams(Map("jarFileName" -> "jar-file-name.jar"))
      ),
      scheduleProperty = CronScheduleProperty("0 0 * * * ?"),
      active = true,
      createdAt = LocalDateTime.now(),
      None
    )
  }

  def buildCanonicalProcess(cronProperty: String = "0 0 * * * ?"): CanonicalProcess = {
    ScenarioBuilder
      .streaming("test")
      .additionalFields(properties = Map(CronPropertyDefaultName -> cronProperty))
      .source("test", "test")
      .emptySink("test", "test")
  }

}
