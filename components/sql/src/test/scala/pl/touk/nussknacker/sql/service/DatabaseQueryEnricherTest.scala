package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.ServiceLogic.{FunctionBasedParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.query.{ResultSetStrategy, UpdateResultStrategy}
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import scala.concurrent.Await

class DatabaseQueryEnricherTest extends BaseHsqlQueryEnricherTest {

  import scala.jdk.CollectionConverters._
  import scala.concurrent.duration._

  override val service =
    new DatabaseQueryEnricher(hsqlDbPoolConfig, new MetaDataProviderFactory().create(hsqlDbPoolConfig))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  test("DatabaseQueryEnricher#implementation without cache") {
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
    val context = Context("1", Map.empty)
    implicit val runContext: RunContext = RunContext(
      collector = collector,
      contextId = ContextId(context.id),
      componentUseCase = componentUseCase
    )
    val serviceLogic = service.implementation(Map.empty, dependencies = Nil, Some(state))
    val paramsEvaluator = new FunctionBasedParamsEvaluator(
      context,
      _ => Map(DatabaseLookupEnricher.KeyValueParamName -> 1L)
    )
    returnType(service, state).display shouldBe "List[Record{ID: Integer, NAME: String}]"
    val resultF = serviceLogic.run(paramsEvaluator)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = serviceLogic.run(paramsEvaluator)
    val result2  = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "Alex"))
    )
  }

  test("DatabaseQueryEnricher#implementation update query") {
    val query = "UPDATE persons SET name = 'Don' where id = ?"
    val st    = conn.prepareStatement(query)
    st.close()
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(Nil),
      strategy = UpdateResultStrategy
    )
    val context = Context("1", Map.empty)
    implicit val runContext: RunContext = RunContext(
      collector = collector,
      contextId = ContextId(context.id),
      componentUseCase = componentUseCase
    )
    val serviceLogic = service.implementation(Map.empty, dependencies = Nil, Some(state))
    val paramsEvaluator = new FunctionBasedParamsEvaluator(
      context,
      _ => Map(DatabaseLookupEnricher.KeyValueParamName -> 1L)
    )
    returnType(service, state).display shouldBe "Integer"
    val resultF = serviceLogic.run(paramsEvaluator)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[Integer]
    result shouldBe 1
    val queryResultSet = conn.prepareStatement("SELECT * FROM persons WHERE id = 1").executeQuery()
    queryResultSet.next()
    queryResultSet.getObject("name") shouldBe "Don"
  }

}
