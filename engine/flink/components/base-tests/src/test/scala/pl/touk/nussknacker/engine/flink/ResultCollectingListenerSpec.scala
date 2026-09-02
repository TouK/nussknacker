package pl.touk.nussknacker.engine.flink

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{ContextId, ContextIdPathPart}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MultiOutputSplitOddEvenInt
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.collection.JavaConverters._

class ResultCollectingListenerSpec
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with LazyLogging
    with VeryPatientScalaFutures
    with FlinkSpec
    with FlinkMiniClusterTestRunner {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  override protected def sourcesWithMockedData: Map[String, List[Int]] = Map(
    "start1" -> List(10, 20, 30, 40),
    "start2" -> List(100, 200, 300, 400),
    "start3" -> List(1, 2, 3, 4),
  )

  override protected def additionalComponents: List[ComponentDefinition] =
    List(ComponentDefinition("splitOddEven", MultiOutputSplitOddEvenInt))

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
        transitionVariables(testResults, "union", Some("end")).size shouldBe 8
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
      }
    )
  }

  // The additional output is interpreted separately from the main chain, so its nodes need their own result entries.
  test("collects results and transitions for nodes on a custom node's additional output") {
    val scenario = ScenarioBuilder
      .streaming("multi-output-additional-results")
      .source("start-foo", "start3")
      .customNodeWithOutputs(
        "split1",
        None,
        "splitOddEven",
        List(
          "main" -> GraphBuilder.emptySink("main-end", "dead-end").get,
          "rejected" -> GraphBuilder
            .buildSimpleVariable("rejVar", "rv", "#input".spel)
            .emptySink("rejected-end", "dead-end")
            .get
        )
      )

    withCollectingTestResults(
      scenario,
      testResults => {
        transitionVariables(testResults, "split1", Some("main-end")) shouldBe Set(
          Map("input" -> 2),
          Map("input" -> 4),
        )
        transitionVariables(testResults, "split1", Some("rejVar")) shouldBe Set(
          Map("input" -> 1),
          Map("input" -> 3),
        )
        transitionVariables(testResults, "rejVar", Some("rejected-end")) shouldBe Set(
          Map("input" -> 1, "rv" -> 1),
          Map("input" -> 3, "rv" -> 3),
        )
        testResults.nodeResults("split1").size shouldBe 4
        testResults.nodeResults("rejVar").size shouldBe 2
        testResults.nodeResults("rejected-end").size shouldBe 2
        testResults.nodeResults("main-end").size shouldBe 2
      }
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
        transitionVariables(testResults, "for-each-1", Some("end")).size shouldBe 8
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
        transitionVariables(testResults, "bv1", Some("end1")).size shouldBe 4
        transitionVariables(testResults, "bv2", Some("end2")).size shouldBe 4
        transitionVariablesByContextId(testResults, "start-foo", Some("split")) shouldBe Map(
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 0) -> Map(
            "input" -> 10
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 1) -> Map(
            "input" -> 20
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 2) -> Map(
            "input" -> 30
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 3) -> Map(
            "input" -> 40
          ),
        )
        transitionVariablesByContextId(testResults, "split", Some("bv1")) shouldBe Map(
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 0,
            path = List(ContextIdPathPart("split", "bv1"))
          ) -> Map("input" -> 10),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 1,
            path = List(ContextIdPathPart("split", "bv1"))
          ) -> Map("input" -> 20),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 2,
            path = List(ContextIdPathPart("split", "bv1"))
          ) -> Map("input" -> 30),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 3,
            path = List(ContextIdPathPart("split", "bv1"))
          ) -> Map("input" -> 40),
        )
        transitionVariablesByContextId(testResults, "split", Some("bv2")) shouldBe Map(
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 0,
            path = List(ContextIdPathPart("split", "bv2"))
          ) -> Map("input" -> 10),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 1,
            path = List(ContextIdPathPart("split", "bv2"))
          ) -> Map("input" -> 20),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 2,
            path = List(ContextIdPathPart("split", "bv2"))
          ) -> Map("input" -> 30),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 3,
            path = List(ContextIdPathPart("split", "bv2"))
          ) -> Map("input" -> 40),
        )
        transitionVariablesByContextId(testResults, "bv1", Some("end1")) shouldBe Map(
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 0,
            path = List(ContextIdPathPart("split", "bv1"))
          ) ->
            Map("input" -> 10, "timesTwo" -> 20),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 1,
            path = List(ContextIdPathPart("split", "bv1"))
          ) ->
            Map("input" -> 20, "timesTwo" -> 40),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 2,
            path = List(ContextIdPathPart("split", "bv1"))
          ) ->
            Map("input" -> 30, "timesTwo" -> 60),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 3,
            path = List(ContextIdPathPart("split", "bv1"))
          ) ->
            Map("input" -> 40, "timesTwo" -> 80),
        )
        transitionVariablesByContextId(testResults, "bv2", Some("end2")) shouldBe Map(
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 0,
            path = List(ContextIdPathPart("split", "bv2"))
          ) ->
            Map("input" -> 10, "timesFour" -> 40),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 1,
            path = List(ContextIdPathPart("split", "bv2"))
          ) ->
            Map("input" -> 20, "timesFour" -> 80),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 2,
            path = List(ContextIdPathPart("split", "bv2"))
          ) ->
            Map("input" -> 30, "timesFour" -> 120),
          ContextId(
            scenarioId = "sample-split",
            originatingNodeId = "start-foo",
            taskId = 0,
            index = 3,
            path = List(ContextIdPathPart("split", "bv2"))
          ) ->
            Map("input" -> 40, "timesFour" -> 160),
        )
      }
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
        transitionVariables(testResults, "sub-fragmentEnd", Some("end")).size shouldBe 3
        assertNumberOfSamplesThatFinishedInNode(testResults, "sub-filter", 1)
        transitionVariables(testResults, "source", Some("sub")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )

        // Transitions withing the fragment graph (fragment has id="sub")
        transitionVariables(testResults, "sub", Some("sub-filter")) shouldBe Set(
          Map("fragment1_input" -> 10),
          Map("fragment1_input" -> 20),
          Map("fragment1_input" -> 30),
          Map("fragment1_input" -> 40),
        )
        transitionVariables(testResults, "sub-filter", None) shouldBe Set(
          Map("fragment1_input" -> 10), // This sample is filtered out and does not proceed further
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

        // Transitions from the single node representing fragment "sub" to final node
        transitionVariables(testResults, "sub", Some("end")) shouldBe Set(
          Map("input" -> 20, "fragmentResult" -> Map("output" -> 20)),
          Map("input" -> 30, "fragmentResult" -> Map("output" -> 30)),
          Map("input" -> 40, "fragmentResult" -> Map("output" -> 40)),
        )
      }
    )
  }

  test("there is a switch") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .switch(
          "switch",
          "''".spel,
          "var",
          Case("#input < 25".spel, GraphBuilder.emptySink("end1", "dead-end")),
          Case("#input >= 25".spel, GraphBuilder.emptySink("end2", "dead-end"))
        )

    withCollectingTestResults(
      scenario,
      testResults => {
        transitionVariables(testResults, "switch", Some("end1")).size shouldBe 2
        transitionVariables(testResults, "switch", Some("end2")).size shouldBe 2
        transitionVariablesByContextId(testResults, "start-foo", Some("switch")) shouldBe Map(
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 0) -> Map(
            "input" -> 10
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 1) -> Map(
            "input" -> 20
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 2) -> Map(
            "input" -> 30
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 3) -> Map(
            "input" -> 40
          ),
        )
        transitionVariablesByContextId(testResults, "switch", Some("end1")) shouldBe Map(
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 0) -> Map(
            "input" -> 10,
            "var"   -> ""
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 1) -> Map(
            "input" -> 20,
            "var"   -> ""
          ),
        )
        transitionVariablesByContextId(testResults, "switch", Some("end2")) shouldBe Map(
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 2) -> Map(
            "input" -> 30,
            "var"   -> ""
          ),
          ContextId(scenarioId = "sample-split", originatingNodeId = "start-foo", taskId = 0, index = 3) -> Map(
            "input" -> 40,
            "var"   -> ""
          ),
        )
      }
    )
  }

  test("there is a logging processor") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .processor("processor", "loggerService", "all" -> "'abrakadabra'".spel)
        .emptySink("end", "dead-end")

    withCollectingTestResults(
      scenario,
      testResults => {
        transitionVariables(testResults, "processor", Some("end")).size shouldBe 4
        transitionVariables(testResults, "start-foo", Some("processor")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "processor", Some("end")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

}
