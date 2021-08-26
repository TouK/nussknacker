package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.OutputVariableNameValue
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{JdbcMetaDataProvider, JdbcMetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BaseDatabaseQueryEnricherTest

import scala.concurrent.Await

class DatabaseLookupEnricherTest extends BaseDatabaseQueryEnricherTest {

  import scala.collection.JavaConverters._
  import scala.concurrent.duration._

  override val prepareDbDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  private val notExistingDbUrl = s"jdbc:hsqldb:mem:dummy"

  private val unknownDbUrl = s"jdbc:hsqldb:mem:dummy"

  private def provider: DBPoolConfig => JdbcMetaDataProvider = (conf: DBPoolConfig) => new JdbcMetaDataProviderFactory().getMetaDataProvider(conf)

  override val service = new DatabaseLookupEnricher(dbConf, provider(dbConf))

  test("DatabaseLookupEnricher#implementation without cache") {
    val query = "select * from persons where id = ?"
    val st = conn.prepareStatement(query)
    val meta = st.getMetaData
    st.close()
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(meta),
      strategy = ResultSetStrategy
    )
    val invoker = service.implementation(Map(), dependencies = Nil, Some(state))
    returnType(service, state).display shouldBe "List[{ID: Integer, NAME: String}]"
    val resultF = invoker.invokeService(Map(DatabaseLookupEnricher.KeyValueParamName -> 1L))
    val result = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = invoker.invokeService(Map(DatabaseLookupEnricher.KeyValueParamName -> 1L))
    val result2 = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "Alex"))
    )
  }

  test("should retrieve table names as parameters") {
    implicit val nodeId: NodeId = NodeId("dummy")
    val definition = service.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val result: service.TransformationStepResult = definition(service.TransformationStep(List(), None))
    result match {
      case service.NextParameters(parameters, _, _) => parameters.head.editor.get shouldBe FixedValuesParameterEditor(List(FixedExpressionValue("'PERSONS'", "PERSONS")))
      case _ =>
    }
  }

  test("should return empty list for not existing database") {
    val newConfig = dbConf.copy(url = notExistingDbUrl)
    val serviceWithNotExistingDatabase = new DatabaseLookupEnricher(newConfig, provider(newConfig))
    implicit val nodeId: NodeId = NodeId("dummy")
    val definition = serviceWithNotExistingDatabase.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val result: serviceWithNotExistingDatabase.TransformationStepResult = definition(serviceWithNotExistingDatabase.TransformationStep(List(), None))
    result match {
      case serviceWithNotExistingDatabase.NextParameters(parameters, _, _) => parameters.head.editor.get shouldBe FixedValuesParameterEditor(List())
      case _ =>
    }
  }

  test("should return empty list for unknown database") {
    val newConfig = dbConf.copy(url = unknownDbUrl)
    val serviceWithNotExistingDatabase = new DatabaseLookupEnricher(newConfig, provider(newConfig))
    implicit val nodeId: NodeId = NodeId("dummy")
    val definition = serviceWithNotExistingDatabase.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val result: serviceWithNotExistingDatabase.TransformationStepResult = definition(serviceWithNotExistingDatabase.TransformationStep(List(), None))
    result match {
      case serviceWithNotExistingDatabase.NextParameters(parameters, _, _) => parameters.head.editor.get shouldBe FixedValuesParameterEditor(List())
      case _ =>
    }
  }

}
