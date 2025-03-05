package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.{Context, NodeId, Params}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.OutputVariableNameValue
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{JdbcMetaDataProvider, MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import java.time.LocalDate
import scala.concurrent.Await

class DatabaseLookupEnricherTest extends BaseHsqlQueryEnricherTest {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40), birth_date DATE);",
    "INSERT INTO persons (id, name, birth_date) VALUES (1, 'John', '1990-08-12')"
  )

  private val notExistingDbUrl = s"jdbc:hsqldb:mem:dummy"

  private val unknownDbUrl = s"jdbc:hsqldb:mem:dummy"

  private def provider: DBPoolConfig => JdbcMetaDataProvider = (conf: DBPoolConfig) =>
    new MetaDataProviderFactory().create(conf)

  override val service = new DatabaseLookupEnricher(hsqlDbPoolConfig, provider(hsqlDbPoolConfig))

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
    val implementation = service.implementation(
      params = Params(
        Map(
          DatabaseQueryEnricher.cacheTTLParamName  -> null,
          DatabaseLookupEnricher.KeyValueParamName -> 1L
        )
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(service, state).display shouldBe "List[Record{BIRTH_DATE: LocalDate, ID: Integer, NAME: String}]"
    val resultF =
      implementation.invoke(Context.withInitialId)
    val result = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John", "BIRTH_DATE" -> LocalDate.parse("1990-08-12")))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 =
      implementation.invoke(Context.withInitialId)
    val result2 = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "Alex", "BIRTH_DATE" -> LocalDate.parse("1990-08-12")))
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
