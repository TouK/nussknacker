package pl.touk.nussknacker.engine.flink.table.source

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.apache.commons.io.FileUtils
import org.apache.flink.configuration.{Configuration, CoreOptions, PipelineOptions}
import org.apache.flink.table.api.{EnvironmentSettings, Schema, TableDescriptor, TableEnvironment}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.flink.table.source.FlinkMiniClusterDataGenerator._
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{DirectoryStream, Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class FlinkMiniClusterDataGenerator(flinkTableSchema: Schema) extends LazyLogging {

  // TODO: check the minicluster releases memory properly and if not, refactor to reuse one minicluster per all usages
  def generateTestData(amountOfRecordsToGenerate: Int): TestData =
    // setting context classloader because Flink in multiple places relies on it and without this temporary override it doesnt have
    // the necessary classes
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      val env = TableEnvironment.create(miniClusterTestingEnvConfig)
      createGeneratorTable(env, amountOfRecordsToGenerate)
      val (tempDirForOutputTable, tempDirUrl) = createTempDirectoryForTestResults()
      createOutputFileTable(env, tempDirUrl)
      val generatedRowsTry = Try {
        insertDataAndAwait(
          env = env,
          inputTableName = dataGenerationInputInternalTableName,
          outputTableName = dataGenerationOutputFileInternalTableName
        )
        readRecordsFromFilesUnderPath(tempDirForOutputTable)
      }
      tryToDeleteDirectoryWithLogging(tempDirForOutputTable)
      val rows = generatedRowsTry.get
      TestData(rows.map(TestRecord(_)))
    }

  private def readRecordsFromFilesUnderPath(path: Path) = {
    val filesUnderPath: List[Path] = {
      val dirStream: DirectoryStream[Path] = Files.newDirectoryStream(path)
      val outputFiles                      = dirStream.asScala.toList
      dirStream.close()
      outputFiles
    }
    filesUnderPath
      .flatMap(f => FileUtils.readLines(f.toFile, StandardCharsets.UTF_8).asScala)
      .map(parse)
      .sequence
      .getOrElse(throw new IllegalStateException("Couldn't parse record from test data dump"))
  }

  private def insertDataAndAwait(env: TableEnvironment, inputTableName: String, outputTableName: String): Unit = {
    val inputTable = env.from(inputTableName)
    inputTable.insertInto(outputTableName).execute().await()
  }

  private def createTempDirectoryForTestResults(): (Path, URL) = {
    val tempDir    = Files.createTempDirectory(tempTestDataOutputTablePrefix)
    val tempDirUrl = tempDir.toUri.toURL
    logger.debug(s"Created temporary directory for dumping test data at: '$tempDirUrl'")
    tempDir -> tempDirUrl
  }

  private def createGeneratorTable(env: TableEnvironment, amountOfRecordsToGenerate: Int): Unit = env.createTable(
    dataGenerationInputInternalTableName,
    TableDescriptor
      .forConnector("datagen")
      .option("number-of-rows", amountOfRecordsToGenerate.toString)
      .schema(flinkTableSchema)
      .build()
  )

  private def createOutputFileTable(env: TableEnvironment, tempDirUrl: URL): Unit = env.createTable(
    dataGenerationOutputFileInternalTableName,
    TableDescriptor
      .forConnector("filesystem")
      .option("path", tempDirUrl.toString)
      .format("json")
      .schema(flinkTableSchema)
      .build()
  )

  private def tryToDeleteDirectoryWithLogging(dirPath: Path): Unit = Try {
    Files
      .walk(dirPath)
      .sorted(java.util.Comparator.reverseOrder())
      .forEach(path => Files.deleteIfExists(path))
    logger.debug(s"Successfully deleted temporary test data dumping directory at: '${dirPath.toUri.toURL}'")
  } match {
    case Failure(e) =>
      logger.error(
        s"Couldn't properly delete temporary test data dumping directory at: '${dirPath.toUri.toURL}'. Exception thrown: $e"
      )
    case Success(_) => ()
  }

}

object FlinkMiniClusterDataGenerator {

  private val dataGenerationInputInternalTableName      = "testDataInputTable"
  private val dataGenerationOutputFileInternalTableName = "testDataOutputTable"
  private val miniClusterTestingEnvParallelism          = Int.box(1)
  private val tempTestDataOutputTablePrefix             = "tableSourceDataDump-"

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
