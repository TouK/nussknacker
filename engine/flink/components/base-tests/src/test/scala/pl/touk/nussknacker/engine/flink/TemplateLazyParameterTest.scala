package pl.touk.nussknacker.engine.flink

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testcomponents.SpelTemplateAstOperationService
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class TemplateLazyParameterTest extends AnyFunSuite with FlinkSpec with Matchers with ValidatedValuesDetailedMessage {

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(
      List(ComponentDefinition("templateAstOperationService", SpelTemplateAstOperationService))
    )
    .build()

  test("should use spel template ast operation parameter") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .enricher(
        "customService",
        "output",
        "templateAstOperationService",
        "template" -> Expression.spelTemplate(s"Hello#{#input}")
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val result = runner.runWithData(scenario, List(1, 2, 3), Boundedness.BOUNDED)
    result.validValue.successes shouldBe List(
      "[Hello]-literal[1]-templated",
      "[Hello]-literal[2]-templated",
      "[Hello]-literal[3]-templated"
    )
  }

}
