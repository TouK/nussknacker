package pl.touk.nussknacker.engine.flink.table.source

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.apache.commons.io.FileUtils
import org.apache.flink.configuration.{Configuration, CoreOptions, PipelineOptions}
import org.apache.flink.table.api.{EnvironmentSettings, Schema, TableDescriptor, TableEnvironment}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.flink.table.source.FlinkMiniClusterDataGenerator._
import pl.touk.nussknacker.engine.flink.table.source.FlinkMiniClusterUtils.{delete, deleteTable}
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.util.UUID
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

class FlinkMiniClusterDataGenerator(flinkTableSchema: Schema) extends LazyLogging {

  private val env = ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
    TableEnvironment.create(miniClusterTestingEnvConfig)
  }

  // TODO: check the minicluster releases memory properly and if not, refactor to reuse one minicluster per all usages
  def generateTestData(amountOfRecordsToGenerate: Int): TestData =
    // setting context classloader because Flink in multiple places relies on it and without this temporary override it doesnt have
    // the necessary classes
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      val inputTableName                           = createGeneratorTable(amountOfRecordsToGenerate)
      val (tempDirForOutputTable, outputTableName) = createOutputFileTable
      val generatedRows = Try {
        insertDataAndAwait(inputTableName, outputTableName)
        readRecordsFromFilesUnderPath(tempDirForOutputTable)
      }
      cleanup(tempDirForOutputTable, List(inputTableName, outputTableName))
      val rows = generatedRows.get
      TestData(rows.map(TestRecord(_)))
    }

  private def readRecordsFromFilesUnderPath(path: Path) = {
    val filesUnderPath = Using(Files.newDirectoryStream(path)) { dirStream =>
      dirStream.asScala.toList
    }.get
    val parsedRecords = filesUnderPath
      .flatMap(f => FileUtils.readLines(f.toFile, StandardCharsets.UTF_8).asScala)
      .map(parse)
      .sequence
    parsedRecords match {
      case Left(ex) =>
        throw new IllegalStateException("Couldn't parse record from test data dump", ex)
      case Right(records) => records
    }
  }

  private def insertDataAndAwait(inputTableName: String, outputTableName: String): Unit = {
    val inputTable = env.from(inputTableName)
    // TODO: Avoid blocking the thread. Refactor `generateTestData` to return future and use a separate blocking thread
    //  pool here
    inputTable.insertInto(outputTableName).execute().await()
  }

  private def createGeneratorTable(amountOfRecordsToGenerate: Int): String = {
    val tableName = generateTestDataInputTableName
    env.createTemporaryTable(
      tableName,
      TableDescriptor
        .forConnector("datagen")
        .option("number-of-rows", amountOfRecordsToGenerate.toString)
        .schema(flinkTableSchema)
        .build()
    )
    tableName
  }

  private def createOutputFileTable: (Path, String) = {
    val tempDir = Files.createTempDirectory(tempTestDataOutputTablePrefix)
    logger.debug(s"Created temporary directory for dumping test data at: '${tempDir.toUri.toURL}'")
    val tableName = generateTestDataOutputTableName
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

  private def cleanup(dir: Path, tableNames: List[String]): Unit = {
    delete(dir)
    tableNames.foreach(t => deleteTable(env, t))
  }

}

object FlinkMiniClusterDataGenerator {

  private def tableNameValidRandomValue        = UUID.randomUUID().toString.replaceAll("-", "")
  private def generateTestDataInputTableName   = s"testDataInputTable_$tableNameValidRandomValue"
  private def generateTestDataOutputTableName  = s"testDataOutputTable_$tableNameValidRandomValue"
  private val miniClusterTestingEnvParallelism = Int.box(1)
  private val tempTestDataOutputTablePrefix    = "tableSourceDataDump-"

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
    EnvironmentSettings.newInstance().withConfiguration(conf).build()
  }

}

object FlinkMiniClusterUtils extends LazyLogging {

  def delete(dir: Path): Unit = Try {
    Files
      .walk(dir)
      .sorted(java.util.Comparator.reverseOrder())
      .forEach(path => Files.deleteIfExists(path))
    logger.debug(s"Successfully deleted temporary test data dumping directory at: '${dir.toUri.toURL}'")
  } match {
    case Failure(e) =>
      logger.error(
        s"Couldn't properly delete temporary test data dumping directory at: '${dir.toUri.toURL}'",
        e
      )
    case Success(_) => ()
  }

  def deleteTable(env: TableEnvironment, tableName: String): Unit = {
    if (!env.dropTemporaryTable(tableName)) {
      logger.error(s"Couldn't properly delete temporary temporary table: '$tableName'")
    }
  }

}
