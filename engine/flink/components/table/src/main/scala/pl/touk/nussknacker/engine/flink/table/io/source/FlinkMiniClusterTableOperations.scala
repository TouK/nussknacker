package pl.touk.nussknacker.engine.flink.table.io.source

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.apache.commons.io.FileUtils
import org.apache.flink.table.api.Expressions.$
import org.apache.flink.table.api._
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.catalog.ObjectIdentifier
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

class FlinkMiniClusterTableOperations(env: StreamTableEnvironment) extends LazyLogging {

  def parseTestRecords(records: List[TestRecord], schema: Schema): List[Row] = {
    val (inputTablePath, inputTableName) = createTempFileTable(schema)
    val parsedRecords = Try {
      writeRecordsToFile(inputTablePath, records)
      val inputTable = env.from(s"`$inputTableName`")
      val iterator   = env.toDataStream(inputTable).executeAndCollect()
      try {
        iterator.asScala.toList
      } finally {
        iterator.close()
      }
    }
    cleanup(inputTablePath)
    parsedRecords.get
  }

  def generateLiveTestData(
      limit: Int,
      schema: Schema,
      tableId: ObjectIdentifier
  ): TestData = generateTestData(
    limit = limit,
    schema = schema,
    sourceTable = createLiveDataGeneratorTable(tableId, schema)
  )

  def generateRandomTestData(amount: Int, schema: Schema): TestData = generateTestData(
    limit = amount,
    schema = schema,
    sourceTable = createRandomDataGeneratorTable(amount, schema)
  )

  private type TableName = String

  private def generateTestData(
      limit: Int,
      schema: Schema,
      sourceTable: Table
  ): TestData = {
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
  ): Table = {
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
      tableId: ObjectIdentifier,
      schema: Schema
  ): Table = {
    env.from(tableId.toString).select(schema.getColumns.asScala.map(_.getName).map($).toList: _*)
  }

  private def createTempFileTable(flinkTableSchema: Schema): (Path, TableName) = {
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

}
