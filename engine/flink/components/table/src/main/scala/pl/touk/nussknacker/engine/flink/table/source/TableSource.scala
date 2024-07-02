package pl.touk.nussknacker.engine.flink.table.source;

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser._
import org.apache.commons.io.FileUtils
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration._
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, TableDescriptor, TableEnvironment}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.SqlFilteringExpression
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{
  BasicContextInitializer,
  ContextInitializer,
  TestDataGenerator,
  TestWithParametersSupport
}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkSourceTestSupport,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.table.TableDefinition
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.source.TableSource._
import pl.touk.nussknacker.engine.flink.table.utils.RowConversions
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.nio.charset.StandardCharsets
import java.nio.file.{DirectoryStream, Files, Path}
import scala.util.{Failure, Success, Try}

class TableSource(
    tableDefinition: TableDefinition,
    sqlStatements: List[SqlStatement],
    enableFlinkBatchExecutionMode: Boolean,
) extends StandardFlinkSource[RECORD]
    with TestWithParametersSupport[RECORD]
    with FlinkSourceTestSupport[RECORD]
    with TestDataGenerator
    with LazyLogging {

  import scala.jdk.CollectionConverters._

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[RECORD] = {
    // TODO: move this to flink-executor - ideally should set be near level of ExecutionConfigPreparer
    if (enableFlinkBatchExecutionMode) {
      env.setRuntimeMode(RuntimeExecutionMode.BATCH)
    }
    val tableEnv = StreamTableEnvironment.create(env);

    sqlStatements.foreach(tableEnv.executeSql)

    val selectQuery = tableEnv.from(tableDefinition.tableName)

    val finalQuery = flinkNodeContext.nodeDeploymentData
      .map { case SqlFilteringExpression(sqlExpression) =>
        tableEnv.executeSql(
          s"CREATE TEMPORARY VIEW $filteringInternalViewName AS SELECT * FROM ${tableDefinition.tableName} WHERE $sqlExpression"
        )
        tableEnv.from(filteringInternalViewName)
      }
      .getOrElse(selectQuery)

    val streamOfRows: DataStream[Row] = tableEnv.toDataStream(finalQuery)

    val streamOfMaps = streamOfRows
      .map(RowConversions.rowToMap)
      .returns(classOf[RECORD])

    new DataStreamSource(streamOfMaps)
  }

  override val contextInitializer: ContextInitializer[RECORD] = new BasicContextInitializer[RECORD](Typed[RECORD])

  override def testParametersDefinition: List[Parameter] =
    tableDefinition.columns.map(c => Parameter(ParameterName(c.columnName), c.typingResult))

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): RECORD = params.map {
    case (paramName, value) => paramName.value -> value.asInstanceOf[Any]
  }.asJava

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[RECORD]] = None

  override def typeInformation: TypeInformation[RECORD] = TypeInformation.of(classOf[RECORD])

//  TODO: add implementation during task with test from file
  override def testRecordParser: TestRecordParser[RECORD] = ???

  override def generateTestData(size: Int): TestData = {
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
          .schema(tableDefinition.toFlinkSchema)
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
            .schema(tableDefinition.toFlinkSchema)
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

  // TODO: how to get path of jar cleaner? Through config?
  private lazy val classPathUrlsForMiniClusterTestingEnv = List(
    "components/flink-table/flinkTable.jar"
  ).map(Path.of(_).toUri.toURL)

  private lazy val miniClusterTestingEnvConfig = {
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

object TableSource {
  private type RECORD = java.util.Map[String, Any]
  private val filteringInternalViewName                 = "filteringView"
  private val dataGenerationInputInternalTableName      = "testDataInputTable"
  private val dataGenerationOutputFileInternalTableName = "testDataOutputTable"
  private val miniClusterTestingEnvParallelism          = Int.box(1)
}
