package pl.touk.nussknacker.engine.flink

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class ScenarioWithoutSinksSpec
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
  )

  test("ending without sink is not allowed - there is a split - fail to compile without sinks in both branches") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .split(
          "split",
          GraphBuilder
            .buildSimpleVariable("bv1", "timesTwo", "#input*2".spel)
            .endWithoutSink,
          GraphBuilder
            .buildSimpleVariable("bv2", "timesFour", "#input*4".spel)
            .endWithoutSink
        )

    catchExceptionMessage(
      withCollectingTestResults(scenario, _ => ())
    ) shouldBe "Compilation errors: InvalidTailOfBranch(Set(bv2, bv1))"

  }

  test("ending without sink is not allowed - there is a split - fail to compile without sink in one of the branches") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .split(
          "split",
          GraphBuilder
            .buildSimpleVariable("bv1", "timesTwo", "#input*2".spel)
            .endWithoutSink,
          GraphBuilder
            .buildSimpleVariable("bv2", "timesFour", "#input*4".spel)
            .emptySink("end", "dead-end")
        )

    catchExceptionMessage(
      withCollectingTestResults(scenario, _ => ())
    ) shouldBe "Compilation errors: InvalidTailOfBranch(Set(bv1))"

  }

  test(
    "ending without sink is allowed - union of two sources with additional variable in only one of the branches without sink"
  ) {
    val scenario =
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
            .endWithoutSink
        )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "union", 8)
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
        transitionVariables(testResults, "union", None) shouldBe Set(
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

  test("ending without sink is allowed - there is a split - without sinks") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .split(
          "split",
          GraphBuilder
            .buildSimpleVariable("bv1", "timesTwo", "#input*2".spel)
            .endWithoutSink,
          GraphBuilder
            .buildSimpleVariable("bv2", "timesFour", "#input*4".spel)
            .endWithoutSink
        )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "bv1", 4)
        assertNumberOfSamplesThatFinishedInNode(testResults, "bv2", 4)
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
        transitionVariables(testResults, "bv1", None) shouldBe Set(
          Map("input" -> 10, "timesTwo" -> 20),
          Map("input" -> 20, "timesTwo" -> 40),
          Map("input" -> 30, "timesTwo" -> 60),
          Map("input" -> 40, "timesTwo" -> 80),
        )
        transitionVariables(testResults, "bv2", None) shouldBe Set(
          Map("input" -> 10, "timesFour" -> 40),
          Map("input" -> 20, "timesFour" -> 80),
          Map("input" -> 30, "timesFour" -> 120),
          Map("input" -> 40, "timesFour" -> 160),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("ending without sink is allowed - there is a split with no continuations") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .split(
          "split",
          GraphBuilder.endWithoutSink,
          GraphBuilder.endWithoutSink
        )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "split", 4)
        transitionVariables(testResults, "start-foo", Some("split")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "split", None) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("ending without sink is allowed - there is a switch with no continuations") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .switch(
          "switch",
          "''".spel,
          "var",
          Case("'1'".spel, GraphBuilder.endWithoutSink),
          Case("'2'".spel, GraphBuilder.endWithoutSink)
        )

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "switch", 4)
        transitionVariables(testResults, "start-foo", Some("switch")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "switch", None) shouldBe Set(
          Map("input" -> 10, "var" -> ""),
          Map("input" -> 20, "var" -> ""),
          Map("input" -> 30, "var" -> ""),
          Map("input" -> 40, "var" -> ""),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("ending without sink is allowed - there is a logging processor with no continuations") {
    val scenario =
      ScenarioBuilder
        .streaming("sample-split")
        .source("start-foo", "start1")
        .processor("processor", "loggerService", "all" -> "'abrakadabra'".spel)
        .endWithoutSink

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "processor", 4)
        transitionVariables(testResults, "start-foo", Some("processor")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "processor", None) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("ending without sink is allowed - there is a fragment - without sink") {
    val scenarioWithFragment =
      ScenarioBuilder
        .streaming("sample-scenario-with-fragment")
        .source("source", "start1")
        .fragment(
          "sub",
          "fragment1",
          List("fragment1_input" -> "#input".spel),
          Map("output"           -> "fragmentResult"),
          Map("output"           -> None)
        )

    val fragment = ScenarioBuilder
      .fragment("fragment1", "fragment1_input" -> classOf[Int])
      .filter("filter", "#fragment1_input != 10".spel)
      .fragmentOutput("fragmentEnd", "output", "output" -> "#fragment1_input".spel)

    val scenario = FragmentResolver(List(fragment)).resolve(scenarioWithFragment).toOption.get

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "sub-fragmentEnd", 3)
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
          None
        ) shouldBe Set(
          Map("input" -> 20, "fragmentResult" -> Map("output" -> 20)),
          Map("input" -> 30, "fragmentResult" -> Map("output" -> 30)),
          Map("input" -> 40, "fragmentResult" -> Map("output" -> 40)),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

  test("ending without sink is allowed - there is for-each node - without sink") {
    val scenario = ScenarioBuilder
      .streaming("sample-for-each")
      .source("start-foo", "start1")
      .customNode("for-each-1", "outForEach1", "for-each", "Elements" -> "{'A', 'B'}".spel)
      .endWithoutSink

    withCollectingTestResults(
      scenario,
      testResults => {
        assertNumberOfSamplesThatFinishedInNode(testResults, "for-each-1", 8)
        transitionVariables(testResults, "start-foo", Some("for-each-1")) shouldBe Set(
          Map("input" -> 10),
          Map("input" -> 20),
          Map("input" -> 30),
          Map("input" -> 40),
        )
        transitionVariables(testResults, "for-each-1", None) shouldBe Set(
          Map("input" -> 10, "outForEach1" -> "A"),
          Map("input" -> 10, "outForEach1" -> "B"),
          Map("input" -> 20, "outForEach1" -> "A"),
          Map("input" -> 20, "outForEach1" -> "B"),
          Map("input" -> 30, "outForEach1" -> "A"),
          Map("input" -> 30, "outForEach1" -> "B"),
          Map("input" -> 40, "outForEach1" -> "A"),
          Map("input" -> 40, "outForEach1" -> "B"),
        )
      },
      allowEndingScenarioWithoutSink = true,
    )
  }

}
