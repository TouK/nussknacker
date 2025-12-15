package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.scalatest.{Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.DefaultConfigCreator
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, SpelParameterEditor}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkNodeCompiler.FlinkNodeCompilerExt
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.{TestNodeCompiler, TestScenarioRunner}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets
import java.time._
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._

class EventGeneratorSourceFactorySpec
    extends AnyFunSuite
    with FlinkSpec
    with PatientScalaFutures
    with Matchers
    with Inside
    with OptionValues {

  private lazy val testScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .build()

  private lazy val nodeCompiler = TestNodeCompiler
    .flinkBased(ConfigFactory.empty())
    .build()

  test("should produce results for each element in list") {
    val input = "some value"

    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "event-generator",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
        "count"    -> "1".spel,
        "value"    -> s"'$input'".spel
      )
      .emptySink("sinkId", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    testScenarioRunner.withRunningScenario(scenario) { fixture =>
      eventually {
        fixture.testSinkResults shouldBe List(input)
      }
    }

  }

  test("should produce n individually evaluated results for n count") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "event-generator",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofMillis(10)".spel,
        "count"    -> "2".spel,
        "value"    -> s"T(java.util.UUID).randomUUID".spel
      )
      .emptySink("sinkId", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    testScenarioRunner.withRunningScenario(scenario) { fixture =>
      eventually {
        val emittedResults = fixture.testSinkResults
        emittedResults.size should be > 1
        emittedResults.distinct.size shouldBe emittedResults.size
      }
    }
  }

  test("should handle complex types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "event-generator",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofMillis(10)".spel,
        "count"    -> "1".spel,
        "value" ->
          """{
              |  "instant": "#{ T(java.time.Instant).ofEpochMilli(123L) }",
              |  "offsetDateTime": "#{ T(java.time.OffsetDateTime).of(2025, 1, 1, 0, 0, 0, 0, T(java.time.ZoneOffset).UTC) }",
              |  "zonedDateTime": "#{ T(java.time.ZonedDateTime).of(2025, 1, 1, 0, 0, 0, 0, T(java.time.ZoneOffset).UTC) }",
              |  "localDateTime": "#{ T(java.time.LocalDateTime).of(2025, 1, 1, 0, 0, 0, 0) }",
              |  "localDate": "#{ T(java.time.LocalDate).of(2025, 1, 1) }",
              |  "localTime": "#{ T(java.time.LocalTime).of(12, 1) }",
              |  "period": "#{ T(java.time.Period).ofDays(30) }",
              |  "duration": "#{ T(java.time.Duration).ofHours(12) }",
              |  "zoneOffset": "#{ T(java.time.ZoneOffset).of("+01:00") }",
              |  "zoneId": "#{ T(java.time.ZoneId).of("Europe/Warsaw") }",
              |  "locale": "#{ T(java.util.Locale).ENGLISH }",
              |  "charset": "#{ T(java.nio.charset.StandardCharsets).UTF_8 }",
              |  "currency": "#{ T(java.util.Currency).getInstance("USD") }",
              |  "uuid": "#{ T(java.util.UUID).fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf") }"
              |}""".stripMargin.jsonTemplate
      )
      // We adds some flink operator to enforce flink messages serialization
      .customNode("foo", "previousOutput", "previousValue", "Key" -> "''".spel, "Value" -> "''".spel)
      .emptySink("sinkId", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    testScenarioRunner.withRunningScenario(scenario) { fixture =>
      val emittedRecord = eventually {
        val emittedResults = fixture.testSinkResults
        emittedResults.size should be > 1
        emittedResults.head
      }
      val expectedRecord = Map(
        "instant"        -> Instant.ofEpochMilli(123L),
        "offsetDateTime" -> OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        "zonedDateTime"  -> ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        "localDateTime"  -> LocalDateTime.of(2025, 1, 1, 0, 0, 0, 0),
        "localDate"      -> LocalDate.of(2025, 1, 1),
        "localTime"      -> LocalTime.of(12, 1),
        "period"         -> Period.ofDays(30),
        "duration"       -> Duration.ofHours(12),
        "zoneOffset"     -> ZoneOffset.of("+01:00"),
        "zoneId"         -> ZoneId.of("Europe/Warsaw"),
        "locale"         -> Locale.ENGLISH,
        "charset"        -> StandardCharsets.UTF_8,
        "currency"       -> Currency.getInstance("USD"),
        "uuid"           -> UUID.fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf"),
      ).asJava
      emittedRecord shouldBe expectedRecord
    }
  }

  test("should handle complex types in heterogeneous context") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "event-generator",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofMillis(10)".spel,
        "count"    -> "1".spel,
        "value" ->
          """{
              |  "instant": "#{ T(java.time.Instant).ofEpochMilli(123L) }",
              |  "offsetDateTime": "#{ T(java.time.OffsetDateTime).of(2025, 1, 1, 0, 0, 0, 0, T(java.time.ZoneOffset).UTC) }",
              |  "zonedDateTime": "#{ T(java.time.ZonedDateTime).of(2025, 1, 1, 0, 0, 0, 0, T(java.time.ZoneOffset).UTC) }",
              |  "localDateTime": "#{ T(java.time.LocalDateTime).of(2025, 1, 1, 0, 0, 0, 0) }",
              |  "localDate": "#{ T(java.time.LocalDate).of(2025, 1, 1) }",
              |  "localTime": "#{ T(java.time.LocalTime).of(12, 1) }",
              |  "period": "#{ T(java.time.Period).ofDays(30) }",
              |  "duration": "#{ T(java.time.Duration).ofHours(12) }",
              |  "zoneOffset": "#{ T(java.time.ZoneOffset).of("+01:00") }",
              |  "zoneId": "#{ T(java.time.ZoneId).of("Europe/Warsaw") }",
              |  "locale": "#{ T(java.util.Locale).ENGLISH }",
              |  "charset": "#{ T(java.nio.charset.StandardCharsets).UTF_8 }",
              |  "currency": "#{ T(java.util.Currency).getInstance("USD") }",
              |  "uuid": "#{ T(java.util.UUID).fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf") }"
              |}""".stripMargin.jsonTemplate
      )
      .buildVariable(
        "mapOfLists",
        "mapOfLists",
        "instant"        -> "{ 123, #input.instant }".spel,
        "offsetDateTime" -> "{ 123, #input.offsetDateTime }".spel,
        "zonedDateTime"  -> "{ 123, #input.zonedDateTime }".spel,
        "localDateTime"  -> "{ 123, #input.localDateTime }".spel,
        "localDate"      -> "{ 123, #input.localDate }".spel,
        "localTime"      -> "{ 123, #input.localTime }".spel,
        "period"         -> "{ 123, #input.period }".spel,
        "duration"       -> "{ 123, #input.duration }".spel,
        "zoneOffset"     -> "{ 123, #input.zoneOffset }".spel,
        "zoneId"         -> "{ 123, #input.zoneId }".spel,
        "locale"         -> "{ 123, #input.locale }".spel,
        "charset"        -> "{ 123, #input.charset }".spel,
        "currency"       -> "{ 123, #input.currency }".spel,
        "uuid"           -> "{ 123, #input.uuid }".spel,
      )
      // We adds some flink operator to enforce flink messages serialization
      .customNode("foo", "previousOutput", "previousValue", "Key" -> "''".spel, "Value" -> "''".spel)
      .emptySink("sinkId", TestScenarioRunner.testResultSink, "value" -> "#mapOfLists".spel)

    testScenarioRunner.withRunningScenario(scenario) { fixture =>
      val emittedRecord = eventually {
        val emittedResults = fixture.testSinkResults
        emittedResults.size should be > 1
        emittedResults.head
      }
      val expectedRecord = Map(
        "instant"        -> List(123, Instant.ofEpochMilli(123L)).asJava,
        "offsetDateTime" -> List(123, OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)).asJava,
        "zonedDateTime"  -> List(123, ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)).asJava,
        "localDateTime"  -> List(123, LocalDateTime.of(2025, 1, 1, 0, 0, 0, 0)).asJava,
        "localDate"      -> List(123, LocalDate.of(2025, 1, 1)).asJava,
        "localTime"      -> List(123, LocalTime.of(12, 1)).asJava,
        "period"         -> List(123, Period.ofDays(30)).asJava,
        "duration"       -> List(123, Duration.ofHours(12)).asJava,
        "zoneOffset"     -> List(123, ZoneOffset.of("+01:00")).asJava,
        "zoneId"         -> List(123, ZoneId.of("Europe/Warsaw")).asJava,
        "locale"         -> List(123, Locale.ENGLISH).asJava,
        "charset"        -> List(123, StandardCharsets.UTF_8).asJava,
        "currency"       -> List(123, Currency.getInstance("USD")).asJava,
        "uuid"           -> List(123, UUID.fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf")).asJava,
      ).asJava
      emittedRecord shouldBe expectedRecord
    }
  }

  test("event generator output context should contain helpers") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "event-generator",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
        "count"    -> "1".spel,
        "value"    -> s"'some value'".spel
      )
      .buildSimpleVariable(
        "variableId",
        "varName",
        "#DATE.now".spel
      )
      .emptySink("sinkId", "dead-end")

    val processValidator = {
      val modelDataWithUtil = LocalModelData(
        ConfigFactory.empty(),
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
        configCreator = new DefaultConfigCreator
      )
      ProcessValidator.default(modelDataWithUtil)
    }
    val scenarioCompilationDependencies = {
      val jobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
      new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)
    }

    val result = processValidator.validate(scenario, isFragment = false)(scenarioCompilationDependencies).result
    result shouldBe Symbol("valid")
  }

  test("node compilation should return number editor for count parameter") {
    val parametersDefinition =
      nodeCompiler.compileNode(Source("source id", SourceRef("event-generator", List.empty))).parameters.value

    val countDefinition = parametersDefinition.find(_.name.value == "count").value
    countDefinition.editors shouldBe List(SpelParameterEditor)
  }

  test("should compute correctly per seconds frequency used in rate limiting strategy") {
    EventGeneratorSourceFactory.computePerSecondsFrequency(Duration.ofSeconds(1)) shouldBe Some(1)
    EventGeneratorSourceFactory.computePerSecondsFrequency(Duration.ofMillis(100)) shouldBe Some(10)
    EventGeneratorSourceFactory.computePerSecondsFrequency(Duration.ofMinutes(1)) shouldBe Some(1.doubleValue() / 60)
    EventGeneratorSourceFactory.computePerSecondsFrequency(Duration.ZERO) shouldBe None
  }

}
