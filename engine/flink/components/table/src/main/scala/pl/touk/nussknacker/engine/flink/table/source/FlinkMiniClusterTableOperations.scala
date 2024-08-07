package pl.touk.nussknacker.engine.flink.table.source

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.apache.commons.io.FileUtils
import org.apache.flink.configuration.{Configuration, CoreOptions, PipelineOptions}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, Schema, Table, TableDescriptor, TableEnvironment}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

object FlinkMiniClusterTableOperations extends LazyLogging {

  def parseTestRecords(records: List[TestRecord], schema: Schema): List[Row] =
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      implicit val env: StreamTableEnvironment = MiniClusterEnvBuilder.buildStreamTableEnv
      val (inputTablePath, inputTableName)     = createTempFileTable(schema)
      val parsedRecords = Try {
        writeRecordsToFile(inputTablePath, records)
        val inputTable = env.from(s"`$inputTableName`")
        env.toDataStream(inputTable).executeAndCollect().asScala.toList
      }
      cleanup(inputTablePath)
      parsedRecords.get
    }

  def generateLiveTestData(
      limit: Int,
      schema: Schema,
      sqlStatements: List[SqlStatement],
      tableName: TableName
  ): TestData = generateTestData(
    limit = limit,
    schema = schema,
    buildSourceTable = createLiveDataGeneratorTable(sqlStatements, tableName, schema)
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
  ): TestData =
    // setting context classloader because Flink in multiple places relies on it and without this temporary override it doesnt have
    // the necessary classes
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      implicit val env: TableEnvironment    = MiniClusterEnvBuilder.buildTableEnv
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
      sqlStatements: List[SqlStatement],
      tableName: TableName,
      schema: Schema
  )(env: TableEnvironment): Table = {
    TableSource.executeSqlDDL(sqlStatements, env)
    env.from(s"`$tableName`").select(schema.getColumns.asScala.map(_.getName).map($).toList: _*)
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

    // TODO: how to get path of jar cleaner? Through config?
    private val classPathUrlsForMiniClusterTestingEnv = List(
      "components/flink-table/flinkTable.jar"
    ).map(Path.of(_).toUri.toURL)

    private val streamEnvConfig = {
      val conf = new Configuration()

      // parent-first - otherwise linkage error (loader constraint violation, a different class with the same name was
      // previously loaded by 'app') for class 'org.apache.commons.math3.random.RandomDataGenerator'
      conf.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

      // without this, on Flink taskmanager level the classloader is basically empty
      conf.set(
        PipelineOptions.CLASSPATHS,
        classPathUrlsForMiniClusterTestingEnv.map(_.toString).asJava
      )
      conf.set(CoreOptions.DEFAULT_PARALLELISM, Int.box(1))
    }

    private val tableEnvConfig = EnvironmentSettings.newInstance().withConfiguration(streamEnvConfig).build()

    def buildTableEnv: TableEnvironment = TableEnvironment.create(tableEnvConfig)

    def buildStreamTableEnv: StreamTableEnvironment = StreamTableEnvironment.create(
      StreamExecutionEnvironment.createLocalEnvironment(streamEnvConfig),
      tableEnvConfig
    )

  }

}
