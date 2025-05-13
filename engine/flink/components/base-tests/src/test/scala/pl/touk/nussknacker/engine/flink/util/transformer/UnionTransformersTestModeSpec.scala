package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class UnionTransformersTestModeSpec
    extends AnyFunSuite
    with BeforeAndAfterEach
    with Matchers
    with FlinkSpec
    with LazyLogging
    with VeryPatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val scenarioName      = "sample-union"
  private val sourceId          = "start-foo"
  private val endSinkId         = "end"
  private val leftBranchId      = "left"
  private val rightBranchId     = "right"
  private val unionNodeId       = "union-node-id"
  private val firstSubtaskIndex = 0
  private val data              = List("10", "20")

  test("should assign unique context ids for union-memo and diamond-like scenario") {
    testDiamondLikeScenario(
      GraphBuilder
        .join(
          unionNodeId,
          "union-memo",
          Some("outVar"),
          List(
            leftBranchId  -> List("key" -> "#input".spel, "value" -> "#input".spel),
            rightBranchId -> List("key" -> "#input".spel, "value" -> "#input".spel)
          ),
          "stateTimeout" -> "T(java.time.Duration).parse('PT1M')".spel
        )
    )
  }

  test("should assign unique context ids for union and diamond-like scenario") {
    testDiamondLikeScenario(
      GraphBuilder
        .join(
          unionNodeId,
          "union",
          Some("outVar"),
          List(
            leftBranchId  -> List("Output expression" -> "#input".spel),
            rightBranchId -> List("Output expression" -> "#input".spel)
          )
        )
    )
  }

  private def testDiamondLikeScenario(unionPart: GraphBuilder[node.SourceNode]): Unit = {
    val scenario = ScenarioBuilder
      .streaming(scenarioName)
      .sources(
        GraphBuilder
          .source(sourceId, "start")
          .split(
            "split",
            GraphBuilder
              .buildSimpleVariable(leftBranchId, leftBranchId, "'a'".spel)
              .branchEnd(leftBranchId, unionNodeId),
            GraphBuilder
              .buildSimpleVariable(rightBranchId, rightBranchId, "'b'".spel)
              .branchEnd(rightBranchId, unionNodeId)
          ),
        unionPart
          .emptySink(endSinkId, "dead-end")
      )
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val modelData = createModelData(data, collectingListener)

      val testResults = collectTestResults(modelData, scenario, collectingListener)

      val contextIds = extractContextIds(testResults)
      contextIds should have size (data.size * 2)
      contextIds should contain theSameElementsAs contextIds.toSet
      contextIds should contain only (
        s"$scenarioName-$sourceId-$firstSubtaskIndex-0-$leftBranchId",
        s"$scenarioName-$sourceId-$firstSubtaskIndex-1-$leftBranchId",
        s"$scenarioName-$sourceId-$firstSubtaskIndex-0-$rightBranchId",
        s"$scenarioName-$sourceId-$firstSubtaskIndex-1-$rightBranchId",
      )
    }
  }

  private def createModelData(
      inputElements: List[String] = List(),
      collectingListener: ResultsCollectingListener[Any]
  ): LocalModelData = {
    val sourceComponent = ComponentDefinition(
      "start",
      SourceFactory.noParamUnboundedStreamFactory[String](
        CollectionSource(inputElements, timestampAssigner = None, returnType = Typed[String])
      )
    )
    LocalModelData(
      ConfigFactory.empty(),
      sourceComponent :: FlinkBaseUnboundedComponentProvider.Components ::: FlinkBaseComponentProvider.Components,
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
  }

  private def collectTestResults[T](
      modelData: LocalModelData,
      testScenario: CanonicalProcess,
      collectingListener: ResultsCollectingListener[T]
  ): TestProcess.TestResults[T] = {
    runScenario(modelData, testScenario)
    collectingListener.results
  }

  private def extractContextIds(results: TestProcess.TestResults[_]): List[String] = results
    .nodeResults(endSinkId)
    .map(_.id)

  private def runScenario(modelData: LocalModelData, scenario: CanonicalProcess): Unit = {
    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = new FlinkScenarioUnitTestJob(modelData).run(scenario, env)
      flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
    }
  }

}
