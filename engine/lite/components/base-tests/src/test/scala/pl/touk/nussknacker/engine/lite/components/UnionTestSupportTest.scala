package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{MultipleSourcesScenarioTestData, NewLineSplittedTestDataParser, TestData, TestDataParser}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke, NodeId, VariableConstants}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource
import pl.touk.nussknacker.engine.lite.components.UnionTestSupportTest.SampleConfigCreator
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.{NodeResult, ResultContext}

import java.nio.charset.StandardCharsets

class UnionTestSupportTest extends AnyFunSuite with Matchers {

  private val modelData: LocalModelData = LocalModelData(ConfigFactory.empty(), SampleConfigCreator)
  private val scenarioName = "scenario1"

  test("should test multiple source scenario with union") {
    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .sources(
        GraphBuilder.source("source1", "source").branchEnd("branch1", "join1"),
        GraphBuilder.source("source2", "source").branchEnd("branch2", "join1"),
        GraphBuilder
          .join("join1", "union", Some("unionOutput"),
            List(
              "branch1" -> List("Output expression" -> "#input"),
              "branch2" -> List("Output expression" -> "#input"))
          )
          .emptySink("end", "dead-end")
      )
    val testData = MultipleSourcesScenarioTestData(
      Map(
        "source1" -> TestData("source1-firstRecord\nsource1-secondRecord".getBytes(StandardCharsets.UTF_8)),
        "source2" -> TestData("source2-firstRecord".getBytes(StandardCharsets.UTF_8)),
      ),
      samplesLimit = 2
    )

    val results = SynchronousLiteInterpreter.test(modelData, scenario, testData)

    results.nodeResults("end") shouldBe List(unionNodeResult("source1", 0, "source1-firstRecord"),
      unionNodeResult("source1", 1, "source1-secondRecord"), unionNodeResult("source2", 0, "source2-firstRecord"))
  }

  private def unionNodeResult(sourceId: String, count: Int, unionValue: String): NodeResult[Any] = {
    NodeResult(ResultContext[Any](s"$scenarioName-$sourceId-$count", Map("unionOutput" -> unionValue)))
  }

}

object UnionTestSupportTest {

  object SampleConfigCreator extends EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map("source" -> WithCategories.anyCategory(SampleSourceFactory))
  }

  object SampleSourceFactory extends SourceFactory {

    @MethodToInvoke
    def create(implicit implicitNodeId: NodeId): Source = {
      new BaseLiteSource[String] with SourceTestSupport[String] {
        override def nodeId: NodeId = implicitNodeId

        override def transform(record: String): Context = Context(contextIdGenerator.nextContextId()).withVariable(VariableConstants.InputVariableName, record)

        override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
          override def parseElement(testElement: String): String = testElement
        }
      }
    }
  }
}
