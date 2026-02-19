package pl.touk.nussknacker.engine.flink.table.source

import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.livedata.LiveDataProvider
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.FlinkTableDataSourceComponentProvider
import pl.touk.nussknacker.engine.flink.util.test.FlinkNodeCompiler.FlinkNodeCompilerExt
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestNodeCompiler
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant

class TableSourceLiveDataFetchingTest extends AnyFunSuite with BeforeAndAfterAll with Matchers {

  private val miniClusterWithServices =
    FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private val configuredEventTimeTimestampTableName = "configured_event_time_timestamp_table"

  private val configuredEventTimeTimestampLtzTableName = "configured_event_time_timestamp_ltz_table"

  private val configuredEventTimeTimestampDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$configuredEventTimeTimestampTableName")

  private val configuredEventTimeTimestampLtzDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$configuredEventTimeTimestampLtzTableName")

  private val nodeCompiler = TestNodeCompiler
    .flinkBased(ConfigFactory.empty())
    .withFlinkMiniCluster(miniClusterWithServices)
    .withExtraComponents(
      FlinkTableDataSourceComponentProvider.create(
        ConfigFactory.parseString(
          s"""tableDefinition: \"\"\"
             |CREATE TABLE $configuredEventTimeTimestampTableName(
             |  event_timestamp TIMESTAMP(3),
             |  another_timestamp_column TIMESTAMP(3),
             |  WATERMARK FOR event_timestamp AS event_timestamp - INTERVAL '1' MINUTE
             |) WITH (
             |  'connector' = 'filesystem',
             |  'path' = 'file:///$configuredEventTimeTimestampDirectory',
             |  'format' = 'json'
             |);
             |CREATE TABLE $configuredEventTimeTimestampLtzTableName(
             |  event_timestamp TIMESTAMP_LTZ(3),
             |  another_timestamp_column TIMESTAMP_LTZ(3),
             |  WATERMARK FOR event_timestamp AS event_timestamp - INTERVAL '1' MINUTE
             |) WITH (
             |  'connector' = 'filesystem',
             |  'path' = 'file:///$configuredEventTimeTimestampLtzDirectory',
             |  'format' = 'json'
             |);
             |\"\"\"
             """.stripMargin
        )
      )
    )
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
    FileUtils.deleteQuietly(configuredEventTimeTimestampDirectory.toFile)
    FileUtils.deleteQuietly(configuredEventTimeTimestampLtzDirectory.toFile)
  }

  test("should return timestamp based on upstream event time configured as TIMESTAMP_LTZ") {
    val inputContent = Json
      .fromFields(
        List(
          "event_timestamp"          -> Json.fromString("2025-01-01 12:01:02.003Z"),
          "another_timestamp_column" -> Json.fromString("2025-12-30 00:00:00Z"),
        )
      )
      .noSpaces

    Files.writeString(
      configuredEventTimeTimestampLtzDirectory.resolve("file.json"),
      inputContent,
      StandardCharsets.UTF_8
    )
    val record = getLoneLiveDataRecord(configuredEventTimeTimestampLtzTableName)

    record.upstreamTimestamp.value shouldBe Instant.parse("2025-01-01T12:01:02.003Z")
  }

  test("should return no timestamp if the rowtime is configured as timestamp without time zone (TIMESTAMP)") {
    val inputContent = Json
      .fromFields(
        List(
          "event_timestamp"          -> Json.fromString("2025-01-01 12:01:02.003"),
          "another_timestamp_column" -> Json.fromString("2025-12-30 00:00:00"),
        )
      )
      .noSpaces

    Files.writeString(configuredEventTimeTimestampDirectory.resolve("file.json"), inputContent, StandardCharsets.UTF_8)
    val record = getLoneLiveDataRecord(configuredEventTimeTimestampTableName)

    record.upstreamTimestamp shouldBe None
  }

  private def getLoneLiveDataRecord(tableName: String) = {
    val liveDataProvider = nodeCompiler
      .compileNode(
        node.Source(
          NodeId("source"),
          NodeName("source"),
          SourceRef(
            "table",
            List(
              Parameter(ParameterName("Table"), s"'`default_catalog`.`default_database`.`$tableName`'".spel)
            )
          )
        )
      )
      .compiledObject
      .validValue
      .asInstanceOf[LiveDataProvider]
    liveDataProvider.fetchLiveData(1).records.loneElement
  }

}
