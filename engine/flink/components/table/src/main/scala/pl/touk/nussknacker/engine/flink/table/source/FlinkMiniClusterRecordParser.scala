package pl.touk.nussknacker.engine.flink.table.source

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.{Configuration, CoreOptions, PipelineOptions}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, Schema, TableDescriptor}
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.flink.table.source.FlinkMiniClusterRecordParser.{
  generateTestDataInputTableName,
  miniClusterTestingEnvConfig
}
import pl.touk.nussknacker.engine.flink.table.source.FlinkMiniClusterUtils.{delete, deleteTable}
import pl.touk.nussknacker.engine.flink.table.source.TableSource.RECORD
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions.rowToMap
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import scala.jdk.CollectionConverters._

class FlinkMiniClusterRecordParser(flinkTableSchema: Schema) extends LazyLogging {

  private val env: StreamTableEnvironment = ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
    val streamEnv = StreamExecutionEnvironment.createLocalEnvironment(miniClusterTestingEnvConfig)
    StreamTableEnvironment.create(
      streamEnv,
      EnvironmentSettings.newInstance().withConfiguration(miniClusterTestingEnvConfig).build()
    )
  }

  // TODO local: refactor method
  def parse(records: List[TestRecord]): List[RECORD] =
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      val (inputTablePath, inputTableName) = createInputFileTable
      writeRecordsToFile(inputTablePath, records)

      val inputTable   = env.from(inputTableName)
      val streamOfRows = env.toDataStream(inputTable).executeAndCollect().asScala.toList

      val maps = streamOfRows.map(rowToMap)

      delete(inputTablePath)
      deleteTable(env, inputTableName)

      maps
    }

  // TODO local: deduplicate with FlinkMiniClusterDataGenerator
  private def createInputFileTable: (Path, String) = {
    val tempDir = Files.createTempDirectory(generateTestDataInputTableName)
    logger.debug(s"Created temporary directory for dumping test data at: '${tempDir.toUri.toURL}'")
    val tableName = generateTestDataInputTableName
    env.createTemporaryTable(
      tableName,
      TableDescriptor
        .forConnector("filesystem")
        .option("path", tempDir.toUri.toURL.toString)
        .format("json")
        .schema(flinkTableSchema)
        .build()
    )
    tempDir -> tableName
  }

  private def writeRecordsToFile(path: Path, records: List[TestRecord]): Unit = {
    val jsonRecords: List[String] = records.map(_.json.noSpaces)
    val jsonFilePath              = path.resolve("output.ndjson")
    val content                   = jsonRecords.mkString("\n")
    Files.write(jsonFilePath, content.getBytes, StandardOpenOption.CREATE)
  }

}

// TODO local: deduplicate with FlinkMiniClusterDataGenerator
object FlinkMiniClusterRecordParser {

  private def tableNameValidRandomValue        = UUID.randomUUID().toString.replaceAll("-", "")
  private def generateTestDataInputTableName   = s"testDataInputTable_$tableNameValidRandomValue"
  private val miniClusterTestingEnvParallelism = Int.box(1)

  // TODO: how to get path of jar cleaner? Through config?
  private val classPathUrlsForMiniClusterTestingEnv = List(
    "components/flink-table/flinkTable.jar"
  ).map(Path.of(_).toUri.toURL)

  private val miniClusterTestingEnvConfig = {
    val conf = new Configuration()

    // parent-first - otherwise linkage error (loader constraint violation, a different class with the same name was
    // previously loaded by 'app') for class 'org.apache.commons.math3.random.RandomDataGenerator'
    conf.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

    // without this, on Flink taskmanager level the classloader is basically empty
    conf.set(
      PipelineOptions.CLASSPATHS,
      classPathUrlsForMiniClusterTestingEnv.map(_.toString).asJava
    )
    conf.set(CoreOptions.DEFAULT_PARALLELISM, miniClusterTestingEnvParallelism)
  }

}
