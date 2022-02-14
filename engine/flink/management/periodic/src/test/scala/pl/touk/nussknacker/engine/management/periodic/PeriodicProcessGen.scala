package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName

object PeriodicProcessGen {
  def apply(cronProperty: String = "0 0 * * * ?"): CanonicalProcess = {
      EspProcessBuilder
        .id("test")
        .additionalFields(properties = Map(CronPropertyDefaultName -> cronProperty))
        .source("test", "test")
        .emptySink("test", "test")
        .toCanonicalProcess
  }
}
