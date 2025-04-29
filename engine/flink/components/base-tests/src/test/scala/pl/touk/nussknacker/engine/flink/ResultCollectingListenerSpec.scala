package pl.touk.nussknacker.engine.flink

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, Service}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.flink.ResultCollectingListenerSpec.AllowingToEndScenarioWithoutSink
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateWindowsConfig
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.testmode.TestProcess.{NodeTransition, TestResults}
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters._
import scala.util.Try

class ResultCollectingListenerSpec
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with LazyLogging
    with VeryPatientScalaFutures
    with FlinkSpec {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val data1 = List(10, 20, 30, 40)
  private val data2 = List(100, 200, 300, 400)

  test("union of two sources with additional variable in only one of the branches") {
    val scenario = ScenarioBuilder
      .streaming("sample-union")
      .sources(
        GraphBuilder
          .source("start-foo", "start1")
          .branchEnd("foo", "union"),
        GraphBuilder
          .source("start-bar", "start2")
          .buildSimpleVariable("bv1", "customVariableInBarBranch", "#input/2".spel)
          .branchEnd("bar", "union"),
        GraphBuilder
          .join(
            "union",
            "union",
            Some("dataIsFrom"),
            List(
              "foo" -> List("Output expression" -> "'foo source'".spel),
              "bar" -> List("Output expression" -> "'bar source'".spel)
            )
          )
          .emptySink("end", "dead-end")
      )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "end", 8)
        transitionVariables(testResults, "start-foo", Some("union")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "start-bar", Some("bv1")) shouldBe Set(
          Map("input" -> 100),
          Map("input" -> 200),
          Map("input" -> 300),
          Map("input" -> 400),
        )
        transitionVariables(testResults, "bv1", Some("union")) shouldBe Set(
          Map("input" -> 100, "customVariableInBarBranch" -> 50),
          Map("input" -> 200, "customVariableInBarBranch" -> 100),
          Map("input" -> 300, "customVariableInBarBranch" -> 150),
          Map("input" -> 400, "customVariableInBarBranch" -> 200),
        )
        transitionVariables(testResults, "union", Some("end")) shouldBe Set(
          Map("input" -> 10, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 20, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 30, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 40, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 100, "customVariableInBarBranch" -> 50, "dataIsFrom"  -> "bar source"),
          Map("input" -> 200, "customVariableInBarBranch" -> 100, "dataIsFrom" -> "bar source"),
          Map("input" -> 300, "customVariableInBarBranch" -> 150, "dataIsFrom" -> "bar source"),
          Map("input" -> 400, "customVariableInBarBranch" -> 200, "dataIsFrom" -> "bar source"),
        )
        transitionVariables(testResults, "end", None) shouldBe Set(
          Map("input" -> 10, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 20, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 30, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 40, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 100, "customVariableInBarBranch" -> 50, "dataIsFrom"  -> "bar source"),
          Map("input" -> 200, "customVariableInBarBranch" -> 100, "dataIsFrom" -> "bar source"),
          Map("input" -> 300, "customVariableInBarBranch" -> 150, "dataIsFrom" -> "bar source"),
          Map("input" -> 400, "customVariableInBarBranch" -> 200, "dataIsFrom" -> "bar source"),
        )
      }
    )
  }

  test("union of two sources with additional variable in only one of the branches - without sinks") {
    val scenario = removeSinks(
      ScenarioBuilder
        .streaming("sample-union")
        .sources(
          GraphBuilder
            .source("start-foo", "start1")
            .branchEnd("foo", "union"),
          GraphBuilder
            .source("start-bar", "start2")
            .buildSimpleVariable("bv1", "customVariableInBarBranch", "#input/2".spel)
            .branchEnd("bar", "union"),
          GraphBuilder
            .join(
              "union",
              "union",
              Some("dataIsFrom"),
              List(
                "foo" -> List("Output expression" -> "'foo source'".spel),
                "bar" -> List("Output expression" -> "'bar source'".spel)
              )
            )
            .emptySink("end", "dead-end") // sink must be created by the DSL, but is then removed
        )
    )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "artificialDeadEndSink-after-union", 8)
        transitionVariables(testResults, "start-foo", Some("union")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "start-bar", Some("bv1")) shouldBe Set(
          Map("input" -> 100),
          Map("input" -> 200),
          Map("input" -> 300),
          Map("input" -> 400),
        )
        transitionVariables(testResults, "bv1", Some("union")) shouldBe Set(
          Map("input" -> 100, "customVariableInBarBranch" -> 50),
          Map("input" -> 200, "customVariableInBarBranch" -> 100),
          Map("input" -> 300, "customVariableInBarBranch" -> 150),
          Map("input" -> 400, "customVariableInBarBranch" -> 200),
        )
        transitionVariables(testResults, "union", Some("artificialDeadEndSink-after-union")) shouldBe Set(
          Map("input" -> 10, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 20, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 30, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 40, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 100, "customVariableInBarBranch" -> 50, "dataIsFrom"  -> "bar source"),
          Map("input" -> 200, "customVariableInBarBranch" -> 100, "dataIsFrom" -> "bar source"),
          Map("input" -> 300, "customVariableInBarBranch" -> 150, "dataIsFrom" -> "bar source"),
          Map("input" -> 400, "customVariableInBarBranch" -> 200, "dataIsFrom" -> "bar source"),
        )
        transitionVariables(testResults, "artificialDeadEndSink-after-union", None) shouldBe Set(
          Map("input" -> 10, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 20, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 30, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 40, "dataIsFrom"                 -> "foo source"),
          Map("input" -> 100, "customVariableInBarBranch" -> 50, "dataIsFrom"  -> "bar source"),
          Map("input" -> 200, "customVariableInBarBranch" -> 100, "dataIsFrom" -> "bar source"),
          Map("input" -> 300, "customVariableInBarBranch" -> 150, "dataIsFrom" -> "bar source"),
          Map("input" -> 400, "customVariableInBarBranch" -> 200, "dataIsFrom" -> "bar source"),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("there is for-each node") {
    val scenario = ScenarioBuilder
      .streaming("sample-for-each")
      .source("start-foo", "start1")
      .customNode("for-each-1", "outForEach1", "for-each", "Elements" -> "{'A', 'B'}".spel)
      .emptySink("end", "dead-end")

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "end", 8)
        transitionVariables(testResults, "start-foo", Some("for-each-1")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "for-each-1", Some("end")) shouldBe Set(
          Map("input" -> 10, "outForEach1" -> "A"),
          Map("input" -> 10, "outForEach1" -> "B"),
          Map("input" -> 20, "outForEach1" -> "A"),
          Map("input" -> 20, "outForEach1" -> "B"),
          Map("input" -> 30, "outForEach1" -> "A"),
          Map("input" -> 30, "outForEach1" -> "B"),
          Map("input" -> 40, "outForEach1" -> "A"),
          Map("input" -> 40, "outForEach1" -> "B"),
        )
        transitionVariables(testResults, "end", None) shouldBe Set(
          Map("input" -> 10, "outForEach1" -> "A"),
          Map("input" -> 10, "outForEach1" -> "B"),
          Map("input" -> 20, "outForEach1" -> "A"),
          Map("input" -> 20, "outForEach1" -> "B"),
          Map("input" -> 30, "outForEach1" -> "A"),
          Map("input" -> 30, "outForEach1" -> "B"),
          Map("input" -> 40, "outForEach1" -> "A"),
          Map("input" -> 40, "outForEach1" -> "B"),
        )
      }
    )
  }

  test("there is a split") {
    val scenario = ScenarioBuilder
      .streaming("sample-split")
      .source("start-foo", "start1")
      .split(
        "split",
        GraphBuilder
          .buildSimpleVariable("bv1", "timesTwo", "#input*2".spel)
          .emptySink("end1", "dead-end"),
        GraphBuilder
          .buildSimpleVariable("bv2", "timesFour", "#input*4".spel)
          .emptySink("end2", "dead-end")
      )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "end1", 4)
        assertNumberOfSamplesThatFinishedInNode(testResults, "end2", 4)
        transitionVariables(testResults, "start-foo", Some("split")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "split", Some("bv1")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "split", Some("bv2")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "bv1", Some("end1")) shouldBe Set(
          Map("input" -> 10, "timesTwo" -> 20),
          Map("input" -> 20, "timesTwo" -> 40),
          Map("input" -> 30, "timesTwo" -> 60),
          Map("input" -> 40, "timesTwo" -> 80),
        )
        transitionVariables(testResults, "bv2", Some("end2")) shouldBe Set(
          Map("input" -> 10, "timesFour" -> 40),
          Map("input" -> 20, "timesFour" -> 80),
          Map("input" -> 30, "timesFour" -> 120),
          Map("input" -> 40, "timesFour" -> 160),
        )
        transitionVariables(testResults, "end1", None) shouldBe Set(
          Map("input" -> 10, "timesTwo" -> 20),
          Map("input" -> 20, "timesTwo" -> 40),
          Map("input" -> 30, "timesTwo" -> 60),
          Map("input" -> 40, "timesTwo" -> 80),
        )
        transitionVariables(testResults, "end2", None) shouldBe Set(
          Map("input" -> 10, "timesFour" -> 40),
          Map("input" -> 20, "timesFour" -> 80),
          Map("input" -> 30, "timesFour" -> 120),
          Map("input" -> 40, "timesFour" -> 160),
        )
      }
    )
  }

  test("there is a split - fail to compile without sinks") {
    val scenario = removeSinks(
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .split(
          "split",
          GraphBuilder
            .buildSimpleVariable("bv1", "timesTwo", "#input*2".spel)
            .emptySink("end1", "dead-end"),
          GraphBuilder
            .buildSimpleVariable("bv2", "timesFour", "#input*4".spel)
            .emptySink("end2", "dead-end")
        )
    )

    catchExceptionMessage(
      withCollectingTestResults(scenario, _ => ())
    ) shouldBe "Compilation errors: InvalidTailOfBranch(Set(bv2, bv1))"

  }

  test("there is a split - without sinks") {
    val scenario = removeSinks(
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .split(
          "split",
          GraphBuilder
            .buildSimpleVariable("bv1", "timesTwo", "#input*2".spel)
            .emptySink("end1", "dead-end"),
          GraphBuilder
            .buildSimpleVariable("bv2", "timesFour", "#input*4".spel)
            .emptySink("end2", "dead-end")
        )
    )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "artificialDeadEndSink-after-bv1", 4)
        assertNumberOfSamplesThatFinishedInNode(testResults, "artificialDeadEndSink-after-bv2", 4)
        transitionVariables(testResults, "start-foo", Some("split")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "split", Some("bv1")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "split", Some("bv2")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "bv1", Some("artificialDeadEndSink-after-bv1")) shouldBe Set(
          Map("input" -> 10, "timesTwo" -> 20),
          Map("input" -> 20, "timesTwo" -> 40),
          Map("input" -> 30, "timesTwo" -> 60),
          Map("input" -> 40, "timesTwo" -> 80),
        )
        transitionVariables(testResults, "bv2", Some("artificialDeadEndSink-after-bv2")) shouldBe Set(
          Map("input" -> 10, "timesFour" -> 40),
          Map("input" -> 20, "timesFour" -> 80),
          Map("input" -> 30, "timesFour" -> 120),
          Map("input" -> 40, "timesFour" -> 160),
        )
        transitionVariables(testResults, "artificialDeadEndSink-after-bv1", None) shouldBe Set(
          Map("input" -> 10, "timesTwo" -> 20),
          Map("input" -> 20, "timesTwo" -> 40),
          Map("input" -> 30, "timesTwo" -> 60),
          Map("input" -> 40, "timesTwo" -> 80),
        )
        transitionVariables(testResults, "artificialDeadEndSink-after-bv2", None) shouldBe Set(
          Map("input" -> 10, "timesFour" -> 40),
          Map("input" -> 20, "timesFour" -> 80),
          Map("input" -> 30, "timesFour" -> 120),
          Map("input" -> 40, "timesFour" -> 160),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("there is a fragment") {
    val scenarioWithFragment = ScenarioBuilder
      .streaming("sample-scenario-with-fragment")
      .source("source", "start1")
      .fragment(
        "sub",
        "fragment1",
        List("fragment1_input" -> "#input".spel),
        Map("output"           -> "fragmentResult"),
        Map("output"           -> GraphBuilder.emptySink("end", "dead-end"))
      )

    val fragment = ScenarioBuilder
      .fragment("fragment1", "fragment1_input" -> classOf[Int])
      .filter("filter", "#fragment1_input != 10".spel)
      .fragmentOutput("fragmentEnd", "output", "output" -> "#fragment1_input".spel)

    val scenario = FragmentResolver(List(fragment)).resolve(scenarioWithFragment).toOption.get

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "end", 3)
        assertNumberOfSamplesThatFinishedInNode(testResults, "sub-filter", 1)
        transitionVariables(testResults, "source", Some("sub")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "sub", Some("sub-filter")) shouldBe Set(
          Map("fragment1_input" -> 10),
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        transitionVariables(testResults, "sub", Some("sub-filter")) shouldBe Set(
          Map("fragment1_input" -> 10),
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        // This sample is filtered out and does not proceed further
        transitionVariables(testResults, "sub-filter", None) shouldBe Set(
          Map("fragment1_input" -> 10),
        )
        transitionVariables(testResults, "sub-filter", Some("sub-fragmentEnd")) shouldBe Set(
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        transitionVariables(testResults, "sub-fragmentEnd", Some("end")) shouldBe Set(
          Map("input" -> 20, "fragmentResult" -> Map("output" -> 20)),
          Map("input" -> 30, "fragmentResult" -> Map("output" -> 30)),
          Map("input" -> 40, "fragmentResult" -> Map("output" -> 40)),
        )
        transitionVariables(testResults, "end", None) shouldBe Set(
          Map("input" -> 20, "fragmentResult" -> Map("output" -> 20)),
          Map("input" -> 30, "fragmentResult" -> Map("output" -> 30)),
          Map("input" -> 40, "fragmentResult" -> Map("output" -> 40)),
        )
      }
    )
  }

  test("there is a fragment - without sinks") {
    val scenarioWithFragment = removeSinks(
      ScenarioBuilder
        .streaming("sample-scenario-with-fragment")
        .source("source", "start1")
        .fragment(
          "sub",
          "fragment1",
          List("fragment1_input" -> "#input".spel),
          Map("output"           -> "fragmentResult"),
          Map("output"           -> GraphBuilder.emptySink("end", "dead-end"))
        )
    )

    val fragment = ScenarioBuilder
      .fragment("fragment1", "fragment1_input" -> classOf[Int])
      .filter("filter", "#fragment1_input != 10".spel)
      .fragmentOutput("fragmentEnd", "output", "output" -> "#fragment1_input".spel)

    val scenario = FragmentResolver(List(fragment)).resolve(scenarioWithFragment).toOption.get

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "artificialDeadEndSink-after-sub-fragmentEnd", 3)
        assertNumberOfSamplesThatFinishedInNode(testResults, "sub-filter", 1)
        transitionVariables(testResults, "source", Some("sub")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "sub", Some("sub-filter")) shouldBe Set(
          Map("fragment1_input" -> 10),
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        transitionVariables(testResults, "sub", Some("sub-filter")) shouldBe Set(
          Map("fragment1_input" -> 10),
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        // This sample is filtered out and does not proceed further
        transitionVariables(testResults, "sub-filter", None) shouldBe Set(
          Map("fragment1_input" -> 10),
        )
        transitionVariables(testResults, "sub-filter", Some("sub-fragmentEnd")) shouldBe Set(
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        transitionVariables(
          testResults,
          "sub-fragmentEnd",
          Some("artificialDeadEndSink-after-sub-fragmentEnd")
        ) shouldBe Set(
          Map("input" -> 20, "fragmentResult" -> Map("output" -> 20)),
          Map("input" -> 30, "fragmentResult" -> Map("output" -> 30)),
          Map("input" -> 40, "fragmentResult" -> Map("output" -> 40)),
        )
        transitionVariables(testResults, "artificialDeadEndSink-after-sub-fragmentEnd", None) shouldBe Set(
          Map("input" -> 20, "fragmentResult" -> Map("output" -> 20)),
          Map("input" -> 30, "fragmentResult" -> Map("output" -> 30)),
          Map("input" -> 40, "fragmentResult" -> Map("output" -> 40)),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  private def transitionVariables(testResults: TestResults[Any], fromNodeId: String, toNodeId: Option[String]) =
    testResults
      .nodeTransitionResults(NodeTransition(fromNodeId, toNodeId))
      .map(_.variables)
      .toSet[Map[String, Any]]
      .map(_.map { case (key, value) => (key, scalaMap(value)) })

  private def scalaMap(value: Any): Any = {
    value match {
      case hashMap: java.util.HashMap[_, _] => hashMap.asScala.toMap
      case other                            => other
    }
  }

  private def assertNumberOfSamplesThatFinishedInNode(testResults: TestResults[Any], sinkId: String, expected: Int) =
    testResults.nodeTransitionResults.get(NodeTransition(sinkId, None)).map(_.length) shouldBe Some(expected)

  private def withCollectingTestResults(
      canonicalProcess: CanonicalProcess,
      assertions: TestResults[Any] => Unit,
      allowEndingScenarioWithoutSink: Boolean = false,
  ): Unit = {
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(collectingListener, AggregateWindowsConfig.Default, allowEndingScenarioWithoutSink)
      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(canonicalProcess, env)
        flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
        assertions(collectingListener.results)
      }
    }
  }

  private def modelData(
      collectingListener: => ResultsCollectingListener[Any],
      aggregateWindowsConfig: AggregateWindowsConfig,
      allowEndingScenarioWithoutSink: Boolean,
  ): LocalModelData = {
    def sourceComponent(data: List[Int]) = SourceFactory.noParamUnboundedStreamFactory[Int](
      EmitWatermarkAfterEachElementCollectionSource
        .create[Int](data, _ => Instant.now.toEpochMilli, Duration.ofHours(1))
    )
    val configCreator = new ConfigCreatorWithCollectingListener(collectingListener)
    val decoratedConfigCreator =
      if (allowEndingScenarioWithoutSink) new AllowingToEndScenarioWithoutSink(configCreator) else configCreator
    LocalModelData(
      ConfigFactory.empty(),
      ComponentDefinition("start1", sourceComponent(data1)) ::
        ComponentDefinition("start2", sourceComponent(data2)) ::
        FlinkBaseUnboundedComponentProvider.create(
          DocsConfig.Default,
          aggregateWindowsConfig
        ) ::: FlinkBaseComponentProvider.Components,
      configCreator = decoratedConfigCreator,
    )
  }

  private def removeSinks(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    canonicalProcess.mapAllNodes(removeSinks)
  }

  private def removeSinks(nodes: List[CanonicalNode]): List[CanonicalNode] = {
    nodes.flatMap {
      case FlatNode(_: node.Sink) =>
        None
      case FlatNode(other) =>
        Some(FlatNode(other))
      case SplitNode(data, nexts) =>
        Some(SplitNode(data, nexts.map(removeSinks)))
      case FilterNode(data, nextFalse) =>
        Some(FilterNode(data, removeSinks(nextFalse)))
      case SwitchNode(data, cases, defaultNext) =>
        Some(SwitchNode(data, cases.map(c => Case(c.expression, removeSinks(c.nodes))), removeSinks(defaultNext)))
      case Fragment(data, outputs) =>
        Some(Fragment(data, outputs.toList.map { case (key, value) => (key, removeSinks(value)) }.toMap))
    }
  }

  private def catchExceptionMessage(f: => Any): String = Try(f).failed.get.getMessage

}

object ResultCollectingListenerSpec {

  class AllowingToEndScenarioWithoutSink(underlying: ProcessConfigCreator) extends ProcessConfigCreator {

    override def modelSettings(modelDependencies: ProcessObjectDependencies): ModelSettings = {
      ModelSettings(allowEndingScenarioWithoutSink = true)
    }

    override def customStreamTransformers(
        modelDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[CustomStreamTransformer]] =
      underlying.customStreamTransformers(modelDependencies)

    override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
      underlying.services(modelDependencies)

    override def sourceFactories(
        modelDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] =
      underlying.sourceFactories(modelDependencies)

    override def sinkFactories(
        modelDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SinkFactory]] =
      underlying.sinkFactories(modelDependencies)

    override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
      underlying.listeners(modelDependencies)

    override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig =
      underlying.expressionConfig(modelDependencies)

    override def modelInfo(): ModelInfo =
      underlying.modelInfo()

  }

}
