package pl.touk.nussknacker.ui.process.periodic.flink

import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.process.periodic.CronScheduleProperty
import pl.touk.nussknacker.ui.process.periodic.cron.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.ui.process.periodic.model.{PeriodicProcess, PeriodicProcessId}

import java.time.LocalDateTime

object PeriodicProcessGen {

  def apply(): PeriodicProcess = {
    PeriodicProcess(
      id = PeriodicProcessId(42),
      deploymentData = DeploymentWithRuntimeParams(
        processId = Some(ProcessId(1)),
        processName = ProcessName(""),
        versionId = VersionId.initialVersionId,
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
      .source("source", ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
  }

}
