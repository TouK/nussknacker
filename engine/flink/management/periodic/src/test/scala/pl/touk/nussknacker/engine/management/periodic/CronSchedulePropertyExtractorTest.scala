package pl.touk.nussknacker.engine.management.periodic

import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

class CronSchedulePropertyExtractorTest extends FunSuite
  with Matchers
  with Inside {

  private val extractor = CronSchedulePropertyExtractor()

  test("should fail for custom process") {
    val result = extractor(CustomProcess("test"))

    inside(result) { case Left("Custom process is not supported") => }
  }

  test("should fail for unparseable process json") {
    val result = extractor(GraphProcess("broken"))

    inside(result) { case Left("Process is unparseable") => }
  }

  test("should fail for missing cron property") {
    val process = GraphProcess(
      ProcessMarshaller.toJson(
        ProcessCanonizer.canonize(
          EspProcessBuilder
            .id("test")
            .exceptionHandler()
            .source("test", "test")
            .sink("test", asSpelExpression("test"), "test")
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
