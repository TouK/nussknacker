package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.management.periodic.model.{DeploymentWithJarData, PeriodicProcess, PeriodicProcessId}

import java.time.LocalDateTime

object PeriodicProcessGen {

  def apply(): PeriodicProcess[CanonicalProcess] = {
    PeriodicProcess(
      id = PeriodicProcessId(42),
      deploymentData = DeploymentWithJarData(
        processName = ProcessVersion.empty.processName,
        versionId = ProcessVersion.empty.versionId,
        process = buildCanonicalProcess(),
        inputConfigDuringExecutionJson = "{}",
        jarFileName = "jar-file-name.jar"
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
