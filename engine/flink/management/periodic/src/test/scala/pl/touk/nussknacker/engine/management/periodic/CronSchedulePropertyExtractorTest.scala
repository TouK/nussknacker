package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder

class CronSchedulePropertyExtractorTest extends FunSuite
  with Matchers
  with Inside {

  private val extractor = CronSchedulePropertyExtractor()

  test("should fail for missing cron property") {
    val process =
        EspProcessBuilder
          .id("test")
          .source("test", "test")
          .emptySink("test", "test")

    val result = extractor(process.toCanonicalProcess)

    inside(result) { case Left("cron property is missing") => }
  }

  test("should fail for invalid cron property") {
    val result = extractor(PeriodicProcessGen.buildCanonicalProcess(cronProperty = "broken"))

    inside(result) { case Left("Expression 'broken' is not a valid cron expression") => }
  }

  test("should extract cron property") {
    val result = extractor(PeriodicProcessGen.buildCanonicalProcess())
    
    inside(result) { case Right(CronScheduleProperty(_)) => }
  }
}
