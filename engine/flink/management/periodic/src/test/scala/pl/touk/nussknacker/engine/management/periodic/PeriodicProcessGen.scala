package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.periodic.CronPropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

object PeriodicProcessGen {
  def apply(cronProperty: String = "0 0 * * * ?"): GraphProcess = {
    GraphProcess(
      ProcessMarshaller.toJson(
        ProcessCanonizer.canonize(
          EspProcessBuilder
            .id("test")
            .additionalFields(properties = Map(CronPropertyDefaultName -> cronProperty))
            .exceptionHandler()
            .source("test", "test")
            .sink("test", asSpelExpression("test"), "test")
        )
      ).noSpaces
    )
  }
}
