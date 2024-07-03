package pl.touk.nussknacker.engine.flink.table.source

import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import org.apache.commons.io.FileUtils
import org.apache.flink.configuration.{Configuration, CoreOptions, PipelineOptions}
import org.apache.flink.table.api.{EnvironmentSettings, Schema, TableDescriptor, TableEnvironment}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.flink.table.source.FlinkMiniClusterDataGenerator._
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.nio.charset.StandardCharsets
import java.nio.file.{DirectoryStream, Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import cats.implicits._

class FlinkMiniClusterDataGenerator(flinkTableSchema: Schema) extends LazyLogging {

  def generateTestData(size: Int): TestData = {
    val classLoader = getClass.getClassLoader

    // setting context classloader because Flink in multiple places relies on it and without this temporary override it doesnt have
    // the necessary classes
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      // TODO: check if this miniCluster env releases memory properly and if not, refactor to reuse one minicluster per all usages
      val tableEnv = TableEnvironment.create(
        EnvironmentSettings.newInstance.withConfiguration(miniClusterTestingEnvConfig).build()
      )

      tableEnv.createTable(
        dataGenerationInputInternalTableName,
        TableDescriptor
          .forConnector("datagen")
          .option("number-of-rows", size.toString)
          .schema(flinkTableSchema)
          .build()
      )

      val tempDir    = Files.createTempDirectory("tableSourceDataDump-")
      val tempDirUrl = tempDir.toUri.toURL
      logger.debug(s"Created temporary directory for dumping test data at: '$tempDirUrl'")

      val result = Try {
        tableEnv.createTable(
          dataGenerationOutputFileInternalTableName,
          TableDescriptor
            .forConnector("filesystem")
            .option("path", tempDirUrl.toString)
            .format("json")
            .schema(flinkTableSchema)
            .build()
        )

        val inputTable = tableEnv.from(dataGenerationInputInternalTableName)
        inputTable.insertInto(dataGenerationOutputFileInternalTableName).execute().await()

        val outputFiles: List[Path] = {
          val dirStream: DirectoryStream[Path] = Files.newDirectoryStream(tempDir)
          val outputFiles                      = dirStream.asScala.toList
          dirStream.close()
          outputFiles
        }

        val records = outputFiles
          .flatMap(f => FileUtils.readLines(f.toFile, StandardCharsets.UTF_8).asScala)
          .map(parse)
          .sequence
          .getOrElse(throw new IllegalStateException("Couldn't parse record from test data dump"))

        TestData(records.map(TestRecord(_)))
      }
      tryToDeleteDirectoryWithLogging(tempDir)
      result.get
    }
  }

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
    conf
  }

}
