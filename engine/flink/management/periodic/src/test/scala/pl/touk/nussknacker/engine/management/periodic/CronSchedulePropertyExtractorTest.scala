package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class CronSchedulePropertyExtractorTest extends FunSuite
  with Matchers
  with Inside {

  private val extractor = CronSchedulePropertyExtractor()

  test("should fail for unparseable scenario json") {
    val result = extractor(GraphProcess("broken"))

    inside(result) { case Left("Scenario is unparseable") => }
  }

  test("should fail for missing cron property") {
    val process = GraphProcess(
      ProcessMarshaller.toJson(
        ProcessCanonizer.canonize(
          EspProcessBuilder
            .id("test")
            .source("test", "test")
            .emptySink("test", "test")
        )
      ).noSpaces
    )

    val result = extractor(process)

    inside(result) { case Left("cron property is missing") => }
  }

  test("should fail for invalid cron property") {
    val result = extractor(PeriodicProcessGen(cronProperty = "broken"))

    inside(result) { case Left("Expression 'broken' is not a valid cron expression") => }
  }

  test("should extract cron property") {
    val result = extractor(PeriodicProcessGen())
    
    inside(result) { case Right(CronScheduleProperty(_)) => }
  }
}
