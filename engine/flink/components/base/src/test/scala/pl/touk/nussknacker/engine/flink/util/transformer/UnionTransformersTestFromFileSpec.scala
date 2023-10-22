package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion, Service}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.UnionWithMemoTransformerSpec.OneRecord
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class UnionTransformersTestFromFileSpec
    extends AnyFunSuite
    with BeforeAndAfterEach
    with Matchers
    with FlinkSpec
    with LazyLogging
    with VeryPatientScalaFutures {

  import spel.Implicits._

  private val ScenarioName      = "sample-union"
  private val FirstSubtaskIndex = 0
  private val Source1Id         = "start-foo"
  private val Source2Id         = "start-bar"
  private val EndSinkId         = "end"
  private val data              = List("10", "20")

  test("should assign unique context ids for union-memo") {
    testScenarioWithUnion(
      ScenarioBuilder
        .streaming(ScenarioName)
        .sources(
          GraphBuilder
            .source(Source1Id, "start")
            .branchEnd("foo", "joined-node-id"),
          GraphBuilder
            .source(Source2Id, "start")
            .branchEnd("bar", "joined-node-id"),
          GraphBuilder
            .join(
              "joined-node-id",
              "union-memo",
              Some("outVar"),
              List(
                "foo" -> List("key" -> "#input", "value" -> "#input"),
                "bar" -> List("key" -> "#input", "value" -> "#input")
              ),
              "stateTimeout" -> "T(java.time.Duration).parse('PT1M')"
            )
            .emptySink(EndSinkId, EndSinkId)
        )
    )
  }

  test("should assign unique context ids for union") {
    testScenarioWithUnion(
      ScenarioBuilder
        .streaming(ScenarioName)
        .sources(
          GraphBuilder
            .source(Source1Id, "start")
            .branchEnd("foo", "joined-node-id"),
          GraphBuilder
            .source(Source2Id, "start")
            .branchEnd("bar", "joined-node-id"),
          GraphBuilder
            .join(
              "joined-node-id",
              "union",
              Some("outVar"),
              List(
                "foo" -> List("Output expression" -> "#input"),
                "bar" -> List("Output expression" -> "#input")
              )
            )
            .emptySink(EndSinkId, EndSinkId)
        )
    )
  }

  private def testScenarioWithUnion(scenario: CanonicalProcess): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    val modelData          = createModelData(data, collectingListener)

    val testResults = collectTestResults(modelData, scenario, collectingListener)

    val contextIds = extractContextIds(testResults)
    contextIds.toSet should have size (data.size * 2)
    contextIds should contain only (
      s"$ScenarioName-$Source1Id-$FirstSubtaskIndex-0",
      s"$ScenarioName-$Source1Id-$FirstSubtaskIndex-1",
      s"$ScenarioName-$Source2Id-$FirstSubtaskIndex-0",
      s"$ScenarioName-$Source2Id-$FirstSubtaskIndex-1"
    )
  }

  private def createModelData(
      inputElements: List[String] = List(),
      collectingListener: ResultsCollectingListener
  ): LocalModelData = {
    LocalModelData(
      ConfigFactory.empty(),
      new UnionTransformersTestFromFileSpec.Creator(inputElements, collectingListener)
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
    .nodeResults(EndSinkId)
    .map(_.context.id)

  private def runProcess(modelData: LocalModelData, scenario: CanonicalProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar =
      FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(stoppableEnv, scenario, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(scenario.id)()
  }

}

object UnionTransformersTestFromFileSpec {

  class Creator(inputElements: List[String], collectingListener: ProcessListener) extends EmptyProcessConfigCreator {

    override def customStreamTransformers(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "union"      -> WithCategories(new UnionTransformer(None)),
        "union-memo" -> WithCategories(new UnionWithMemoTransformer(None)),
      )

    override def sourceFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] = {
      implicit val typeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])

      Map(
        "start" -> WithCategories(
          SourceFactory.noParam[String](
            CollectionSource(inputElements, timestampAssigner = None, returnType = Typed[String])
          )
        )
      )
    }

    override def sinkFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SinkFactory]] = {
      Map("end" -> WithCategories(SinkFactory.noParam(EmptySink)))
    }

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      List(collectingListener)
  }

}
