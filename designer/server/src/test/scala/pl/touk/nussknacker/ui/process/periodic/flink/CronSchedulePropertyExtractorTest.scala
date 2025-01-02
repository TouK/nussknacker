package pl.touk.nussknacker.ui.process.periodic.flink

import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.ui.process.periodic.{CronScheduleProperty, CronSchedulePropertyExtractor, MultipleScheduleProperty}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, ValidatedValuesDetailedMessage}
import pl.touk.nussknacker.ui.process.periodic.cron.CronParameterValidator

class CronSchedulePropertyExtractorTest
    extends AnyFunSuite
    with Matchers
    with EitherValuesDetailedMessage
    with ValidatedValuesDetailedMessage
    with Inside {

  private val extractor = CronSchedulePropertyExtractor()

  test("should fail for missing cron property") {
    val process =
      ScenarioBuilder
        .streaming("test")
        .source("test", "test")
        .emptySink("test", "test")

    val result = extractor(process)
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

  test("should extract MultipleScheduleProperty") {
    val multipleSchedulesExpression = "{foo: '0 0 * * * ?', bar: '1 0 * * * ?'}"
    val result                      = extractor(PeriodicProcessGen.buildCanonicalProcess(multipleSchedulesExpression))
    result.rightValue shouldEqual MultipleScheduleProperty(
      Map(
        "foo" -> CronScheduleProperty("0 0 * * * ?"),
        "bar" -> CronScheduleProperty("1 0 * * * ?")
      )
    )

    validate(multipleSchedulesExpression).validValue
  }

  private def validate(expression: String) = {
    CronParameterValidator.isValid(ParameterName("cron"), Expression.spel(s"'$expression'"), Some(expression), None)(
      NodeId("fooNodeId")
    )
  }

}
