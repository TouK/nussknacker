package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.OutputVariableNameValue
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{JdbcMetaDataProvider, MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import java.io.{ByteArrayOutputStream, FileOutputStream, ObjectOutputStream}
import scala.concurrent.Await

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

  test("Database lookup enricher should be serializable") {
    val fos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(fos);
    oos.writeObject(service)
    oos.flush()
    oos.close()
  }

  test("Test on flink") {
    import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
    val testScenarioRunner = TestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .withExtraComponents(List(ComponentDefinition("dbLookup", service)))
      .build()

    val scenario =
      ScenarioBuilder
        .streaming("test")
        .parallelism(1)
        .source("start", "source")
        .enricher(
          id = "personEnricher",
          output = "person",
          svcId = "dbLookup",
          params = "Table" -> Expression.spel("'persons'"),
          "Cache TTL"  -> Expression.spel("T(java.time.Duration).ofDays(1L)"),
          "Key column" -> Expression.spel("'id'"),
          "Key value"  -> Expression.spel("#input")
        )
        .processorEnd("end", "invocationCollector", "value" -> Expression.spel("#person"))

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
