package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
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

  import spel.Implicits._

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
            leftBranchId  -> List("key" -> "#input", "value" -> "#input"),
            rightBranchId -> List("key" -> "#input", "value" -> "#input")
          ),
          "stateTimeout" -> "T(java.time.Duration).parse('PT1M')"
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
            leftBranchId  -> List("Output expression" -> "#input"),
            rightBranchId -> List("Output expression" -> "#input")
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
              .buildSimpleVariable(leftBranchId, leftBranchId, "'a'")
              .branchEnd(leftBranchId, unionNodeId),
            GraphBuilder
              .buildSimpleVariable(rightBranchId, rightBranchId, "'b'")
              .branchEnd(rightBranchId, unionNodeId)
          ),
        unionPart
          .emptySink(endSinkId, endSinkId)
      )
    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    val modelData          = createModelData(data, collectingListener)

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

  private def createModelData(
      inputElements: List[String] = List(),
      collectingListener: ResultsCollectingListener
  ): LocalModelData = {
    LocalModelData(
      ConfigFactory.empty(),
      new UnionTransformersTestModeSpec.Creator(inputElements, collectingListener)
    )
  }

  private def collectTestResults[T](
      modelData: LocalModelData,
      testProcess: CanonicalProcess,
      collectingListener: ResultsCollectingListener
  ): TestProcess.TestResults[Any] = {
    runProcess(modelData, testProcess)
    collectingListener.results[Any]
  }

  private def extractContextIds(results: TestProcess.TestResults[Any]): List[String] = results
    .nodeResults(endSinkId)
    .map(_.context.id)

  private def runProcess(modelData: LocalModelData, scenario: CanonicalProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar =
      FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(stoppableEnv, scenario, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(scenario.id)()
  }

}

object UnionTransformersTestModeSpec {

  class Creator(inputElements: List[String], collectingListener: ProcessListener) extends EmptyProcessConfigCreator {

    override def customStreamTransformers(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "union"      -> WithCategories.anyCategory(new UnionTransformer(None)),
        "union-memo" -> WithCategories.anyCategory(new UnionWithMemoTransformer(None)),
      )

    override def sourceFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] = {
      implicit val typeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])

      Map(
        "start" -> WithCategories.anyCategory(
          SourceFactory.noParam[String](
            CollectionSource(inputElements, timestampAssigner = None, returnType = Typed[String])
          )
        )
      )
    }

    override def sinkFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SinkFactory]] = {
      Map("end" -> WithCategories.anyCategory(SinkFactory.noParam(EmptySink)))
    }

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      List(collectingListener)
  }

}
