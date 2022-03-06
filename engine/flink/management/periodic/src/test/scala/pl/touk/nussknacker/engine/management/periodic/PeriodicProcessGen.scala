package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.management.periodic.model.{DeploymentWithJarData, PeriodicProcess, PeriodicProcessId}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.time.LocalDateTime

object PeriodicProcessGen {
  def apply(): PeriodicProcess = {
    PeriodicProcess(
      id = PeriodicProcessId(42),
      deploymentData = DeploymentWithJarData(
        processVersion = ProcessVersion.empty,
        processJson = "{}",
        inputConfigDuringExecutionJson = "{}",
        jarFileName = "jar-file-name.jar"
      ),
      scheduleProperty = CronScheduleProperty("0 0 * * * ?"),
      active = true,
      createdAt = LocalDateTime.now(),
    )
  }

  def buildCanonicalProcess(cronProperty: String = "0 0 * * * ?"): GraphProcess = {
    GraphProcess(
      ProcessMarshaller.toJson(
        ProcessCanonizer.canonize(
          EspProcessBuilder
            .id("test")
            .additionalFields(properties = Map(CronPropertyDefaultName -> cronProperty))
            .exceptionHandler()
            .source("test", "test")
            .emptySink("test", "test")
        )
      ).noSpaces
    )
  }
}
