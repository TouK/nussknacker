package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.OutputVariableNameValue
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestProcess}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{JdbcMetaDataProvider, MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest
import pl.touk.nussknacker.engine.spel.Implicits._

import java.io.{ByteArrayOutputStream, FileOutputStream, ObjectOutputStream}
import scala.concurrent.Await

case class TestRecord(id: Int = 1, timeHours: Int = 0) {
  def timestamp: Long = timeHours * 3600L * 1000
}

// TODO_PAWEL kolejnosc i czy sie odpalaja wszystkie before all
class DatabaseLookupEnricherTest extends BaseHsqlQueryEnricherTest with FlinkSpec {

  import scala.jdk.CollectionConverters._
  import scala.concurrent.duration._

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  private val notExistingDbUrl = s"jdbc:hsqldb:mem:dummy"

  private val unknownDbUrl = s"jdbc:hsqldb:mem:dummy"

  private def provider: DBPoolConfig => JdbcMetaDataProvider = (conf: DBPoolConfig) =>
    new MetaDataProviderFactory().create(conf)

  override val service = new DatabaseLookupEnricher(hsqlDbPoolConfig, provider(hsqlDbPoolConfig))

  private val sinkId                    = "end"
  private val resultVariableName        = "resultVar"
  private val forEachOutputVariableName = "forEachVar"
  private val forEachNodeResultId       = "for-each-result"

  private def aProcessWithDbLookupNode(): CanonicalProcess = {
    val resultExpression: String = s"#$forEachOutputVariableName.NAME"
    return ScenarioBuilder
      .streaming("forEachProcess")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .enricher(
        id = "personEnricher",
        output = forEachOutputVariableName,
        svcId = "dbLookup",
        params = "Table" -> Expression.spel("'PERSONS'"),
        "Cache TTL"  -> Expression.spel("T(java.time.Duration).ofDays(1L)"),
        "Key column" -> Expression.spel("'ID'"),
        "Key value"  -> Expression.spel("#input.id")
      )
      .buildSimpleVariable(forEachNodeResultId, "resultVar", resultExpression)
      .emptySink(sinkId, "dead-end")
  }

  test("Database lookup enricher should be serializable") {
    val fos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(fos);
    oos.writeObject(service)
    oos.flush()
    oos.close()
  }

  private def initializeListener = ResultsCollectingListenerHolder.registerRun

  private def modelData(
      list: List[TestRecord] = List(),
      collectingListener: ResultsCollectingListener
  ): LocalModelData = {
    val modelConfig = ConfigFactory
      .empty()
      .withValue("useTypingResultTypeInformation", fromAnyRef(true))
    val sourceComponent = ComponentDefinition(
      "start",
      SourceFactory.noParam[TestRecord](
        EmitWatermarkAfterEachElementCollectionSource
          .create[TestRecord](list, _.timestamp, java.time.Duration.ofHours(1))(TypeInformation.of(classOf[TestRecord]))
      )
    )
    LocalModelData(
      modelConfig,
      (sourceComponent :: FlinkBaseComponentProvider.Components) ::: List(ComponentDefinition("dbLookup", service)),
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
  }

  private def collectTestResults[T](
      model: LocalModelData,
      testProcess: CanonicalProcess,
      collectingListener: ResultsCollectingListener
  ): TestProcess.TestResults = {
    runProcess(model, testProcess)
    collectingListener.results
  }

  private def runProcess(model: LocalModelData, testProcess: CanonicalProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(testProcess)
    stoppableEnv.executeAndWaitForFinished(testProcess.name.value)()
  }

  test("should produce results for each element in list") {
    val collectingListener = initializeListener
    val model              = modelData(List(TestRecord(id = 1)), collectingListener)

    val testProcess = aProcessWithDbLookupNode()

    val results = collectTestResults[String](model, testProcess, collectingListener)
    extractResultValues(results) shouldBe List("John")
  }

  private def extractResultValues(results: TestProcess.TestResults): List[String] = results
    .nodeResults(sinkId)
    .map(_.get[String](resultVariableName).get)

  test("Test on flink") {

    val env       = flinkMiniCluster.createExecutionEnvironment()
    val modelData = LocalModelData(ConfigFactory.empty(), List(ComponentDefinition("dbLookup", service)))
    val scenario =
      ScenarioBuilder
        .streaming("test")
        .parallelism(1)
        .source("start", "source")
        .enricher(
          id = "personEnricher",
          output = "person",
          svcId = "dbLookup",
          params = "Table" -> Expression.spel("'PERSONS'"),
          "Cache TTL"  -> Expression.spel("T(java.time.Duration).ofDays(1L)"),
          "Key column" -> Expression.spel("'ID'"),
          "Key value"  -> Expression.spel("#input")
        )
        .processorEnd("end", "invocationCollector", "value" -> Expression.spel("#person"))

    import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
    val testScenarioRunner = TestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .withExtraComponents(List(ComponentDefinition("dbLookup", service)))
      .build()

    val sth = testScenarioRunner.runWithData(scenario, List(1))
    val a   = 5
  }

  test("DatabaseLookupEnricher#implementation without cache") {
    val query = "select * from persons where id = ?"
    val st    = conn.prepareStatement(query)
    val meta  = st.getMetaData
    st.close()
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(meta),
      strategy = ResultSetStrategy
    )
    val invoker = service.implementation(Map(), dependencies = Nil, Some(state))
    returnType(service, state).display shouldBe "List[Record{ID: Integer, NAME: String}]"
    val resultF = invoker.invokeService(Map(DatabaseLookupEnricher.KeyValueParamName -> 1L))
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = invoker.invokeService(Map(DatabaseLookupEnricher.KeyValueParamName -> 1L))
    val result2  = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "Alex"))
    )
  }

  test("should retrieve table names as parameters") {
    implicit val nodeId: NodeId = NodeId("dummy")
    val definition = service.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val result: service.TransformationStepResult = definition(service.TransformationStep(List(), None))
    result match {
      case service.NextParameters(parameters, _, _) =>
        parameters.head.editor.get shouldBe FixedValuesParameterEditor(
          List(FixedExpressionValue("'PERSONS'", "PERSONS"))
        )
      case _ =>
    }
  }

  test("should return empty list for not existing database") {
    val newConfig                      = hsqlDbPoolConfig.copy(url = notExistingDbUrl)
    val serviceWithNotExistingDatabase = new DatabaseLookupEnricher(newConfig, provider(newConfig))
    implicit val nodeId: NodeId        = NodeId("dummy")
    val definition =
      serviceWithNotExistingDatabase.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val result: serviceWithNotExistingDatabase.TransformationStepResult =
      definition(serviceWithNotExistingDatabase.TransformationStep(List(), None))
    result match {
      case serviceWithNotExistingDatabase.NextParameters(parameters, _, _) =>
        parameters.head.editor.get shouldBe FixedValuesParameterEditor(List())
      case _ =>
    }
  }

  test("should return empty list for unknown database") {
    val newConfig                      = hsqlDbPoolConfig.copy(url = unknownDbUrl)
    val serviceWithNotExistingDatabase = new DatabaseLookupEnricher(newConfig, provider(newConfig))
    implicit val nodeId: NodeId        = NodeId("dummy")
    val definition =
      serviceWithNotExistingDatabase.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val result: serviceWithNotExistingDatabase.TransformationStepResult =
      definition(serviceWithNotExistingDatabase.TransformationStep(List(), None))
    result match {
      case serviceWithNotExistingDatabase.NextParameters(parameters, _, _) =>
        parameters.head.editor.get shouldBe FixedValuesParameterEditor(List())
      case _ =>
    }
  }

}
