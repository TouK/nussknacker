package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.{Charset, StandardCharsets}
import java.time.{
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  OffsetDateTime,
  Period,
  ZonedDateTime,
  ZoneId,
  ZoneOffset
}
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._

class EventGeneratorSourceFactorySpec
    extends AnyFunSuite
    with FlinkSpec
    with PatientScalaFutures
    with Matchers
    with Inside {

  test("should produce results for each element in list") {
    val sinkId = "sinkId"
    val input  = "some value"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = LocalModelData(
        ConfigFactory.empty(),
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
        configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
      )
      val scenario = ScenarioBuilder
        .streaming("test")
        .source(
          "event-generator",
          "event-generator",
          "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
          "count"    -> "1".spel,
          "value"    -> s"'$input'".spel
        )
        .emptySink(sinkId, "dead-end")

      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(scenario, env)
        flinkMiniCluster.withRunningJob(executionResult.getJobID) {
          eventually {
            val results = collectingListener.results.nodeResults.get(sinkId)
            results.flatMap(_.headOption).flatMap(_.variableTyped("input")) shouldBe Some(input)
          }
        }
      }
    }

  }

  test("should produce n individually evaluated results for n count") {
    val sinkId = "sinkId"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = LocalModelData(
        ConfigFactory.empty(),
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
        configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
      )
      val scenario = ScenarioBuilder
        .streaming("test")
        .source(
          "event-generator",
          "event-generator",
          "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
          "count"    -> "2".spel,
          "value"    -> s"T(java.util.UUID).randomUUID".spel
        )
        .emptySink(sinkId, "dead-end")

      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(scenario, env)

        flinkMiniCluster.withRunningJob(executionResult.getJobID) {
          eventually {
            val results        = collectingListener.results.nodeResults.get(sinkId)
            val emittedResults = results.toList.flatten.flatMap(_.variableTyped("input"))
            emittedResults.size should be > 1
            emittedResults.distinct.size shouldBe emittedResults.size
          }
        }
      }
    }
  }

  test("should handle complex types") {
    val sinkId = "sinkId"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = LocalModelData(
        ConfigFactory.empty(),
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
        configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
      )
      val scenario = ScenarioBuilder
        .streaming("test")
        .source(
          "event-generator",
          "event-generator",
          "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
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
        .emptySink(sinkId, "dead-end")

      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(scenario, env)

        flinkMiniCluster.withRunningJob(executionResult.getJobID) {
          val emittedRecord = eventually {
            val results        = collectingListener.results.nodeResults.get(sinkId)
            val emittedResults = results.toList.flatten.flatMap(_.variableTyped[Any]("input"))
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
    }
  }

}
