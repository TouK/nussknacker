package pl.touk.nussknacker.engine.flink.table.source

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.apache.commons.io.FileUtils
import org.apache.flink.configuration.{Configuration, CoreOptions, PipelineOptions}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api._
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition._

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

object FlinkMiniClusterTableOperations extends LazyLogging {

  def parseTestRecords(records: List[TestRecord], schema: Schema): List[Row] = {
    Using.resource(MiniClusterEnvBuilder.createLocalStreamEnv) { streamEnv =>
      implicit val tableEvn: StreamTableEnvironment = MiniClusterEnvBuilder.createTableStreamEnv(streamEnv)
      val (inputTablePath, inputTableName)          = createTempFileTable(schema)
      try {
        writeRecordsToFile(inputTablePath, records)
        val inputTable = tableEvn.from(s"`$inputTableName`")
        tableEvn.toDataStream(inputTable).executeAndCollect().asScala.toList
      } finally {
        cleanup(inputTablePath)
      }
    }
  }

  def generateLiveTestData(
      limit: Int,
      schema: Schema,
      flinkDataDefinition: FlinkDataDefinition,
      tableId: ObjectIdentifier
  ): TestData = generateTestData(
    limit = limit,
    schema = schema,
    buildSourceTable = createLiveDataGeneratorTable(flinkDataDefinition, tableId, schema)
  )

  def generateRandomTestData(amount: Int, schema: Schema): TestData = generateTestData(
    limit = amount,
    schema = schema,
    buildSourceTable = createRandomDataGeneratorTable(amount, schema)
  )

  private type TableName = String

  // TODO: check the minicluster releases memory properly and if not, refactor to reuse one minicluster per all usages
  private def generateTestData(
      limit: Int,
      schema: Schema,
      buildSourceTable: TableEnvironment => Table
  ): TestData = {
    implicit val env: TableEnvironment    = MiniClusterEnvBuilder.createTableEnv
    val sourceTable                       = buildSourceTable(env)
    val (outputFilePath, outputTableName) = createTempFileTable(schema)
    val generatedRows = Try {
      insertDataAndAwait(sourceTable, outputTableName, limit)
      readRecordsFromFilesUnderPath(outputFilePath)
    }
    cleanup(outputFilePath)
    val rows = generatedRows.get
    TestData(rows.map(TestRecord(_)))
  }

  private def writeRecordsToFile(path: Path, records: List[TestRecord]): Unit = {
    val jsonRecords: List[String] = records.map(_.json.noSpaces)
    val jsonFilePath              = path.resolve("output.ndjson")
    val content                   = jsonRecords.mkString("\n")
    Files.write(jsonFilePath, content.getBytes, StandardOpenOption.CREATE)
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
      case Left(ex)       => throw new IllegalStateException("Couldn't parse record from test data dump", ex)
      case Right(records) => records
    }
  }

  private def insertDataAndAwait(inputTable: Table, outputTableName: TableName, limit: Int): Unit = {
    // TODO: Avoid blocking the thread. Refactor `generateTestData` to return future and use a separate blocking thread
    //  pool here
    inputTable.limit(limit).insertInto(outputTableName).execute().await()
  }

  private def createRandomDataGeneratorTable(
      amountOfRecordsToGenerate: Int,
      flinkTableSchema: Schema,
  )(env: TableEnvironment): Table = {
    val tableName = generateTableName
    env.createTemporaryTable(
      tableName,
      TableDescriptor
        .forConnector("datagen")
        .option("number-of-rows", amountOfRecordsToGenerate.toString)
        .schema(flinkTableSchema)
        .build()
    )
    env.from(tableName)
  }

  private def createLiveDataGeneratorTable(
      flinkDataDefinition: FlinkDataDefinition,
      tableId: ObjectIdentifier,
      schema: Schema
  )(env: TableEnvironment): Table = {
    flinkDataDefinition.registerIn(env).orFail
    env.from(tableId.toString).select(schema.getColumns.asScala.map(_.getName).map($).toList: _*)
  }

  private def createTempFileTable(flinkTableSchema: Schema)(implicit env: TableEnvironment): (Path, TableName) = {
    val tempTestDataOutputFilePrefix = "tableSourceDataDump-"
    val tempDir                      = Files.createTempDirectory(tempTestDataOutputFilePrefix)
    logger.debug(s"Created temporary directory for dumping test data at: '${tempDir.toUri.toURL}'")
    val tableName = generateTableName
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

  private def cleanup(dir: Path): Unit = Try {
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

  private def generateTableName: TableName = s"testDataInputTable_${UUID.randomUUID().toString.replaceAll("-", "")}"

  private object MiniClusterEnvBuilder {

    private lazy val streamEnvConfig = {
      val conf = new Configuration()

      // parent-first - otherwise linkage error (loader constraint violation, a different class with the same name was
      // previously loaded by 'app') for class 'org.apache.commons.math3.random.RandomDataGenerator'
      conf.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

      // Here is a hidden assumption that getClass.getClassLoader is the model classloader and another hidden assumuption that model classloader has all necessary jars (including connectors)
      // TODO: we should explicitly pass model classloader + we should split model classloader into libs that are only for
      //       testing mechanism purpose (in the real deployment, they are already available in Flink), for example table connectors
      Thread.currentThread().getContextClassLoader match {
        case url: URLClassLoader =>
          conf.set(PipelineOptions.CLASSPATHS, url.getURLs.toList.map(_.toString).asJava)
        case _ =>
          logger.warn(
            "Context classloader is not a URLClassLoader. Probably data generation invocation wasn't wrapped with ModelData.withThisAsContextClassLoader. MiniCluster classpath set up will be skipped."
          )
      }
      conf.set(CoreOptions.DEFAULT_PARALLELISM, Int.box(1))
    }

    private lazy val tableEnvConfig = EnvironmentSettings.newInstance().withConfiguration(streamEnvConfig).build()

    def createTableEnv: TableEnvironment = TableEnvironment.create(tableEnvConfig)

    def createLocalStreamEnv: StreamExecutionEnvironment =
      StreamExecutionEnvironment.createLocalEnvironment(streamEnvConfig)

    def createTableStreamEnv(streamEnv: StreamExecutionEnvironment): StreamTableEnvironment =
      StreamTableEnvironment.create(streamEnv, tableEnvConfig)

  }

}
