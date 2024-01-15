package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.ServiceLogic.{FunctionBasedParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.CacheTTLParamName
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import scala.concurrent.Await

class DatabaseLookupEnricherWithCacheTest extends BaseHsqlQueryEnricherTest {

  import scala.jdk.CollectionConverters._
  import scala.concurrent.duration._

  override val service =
    new DatabaseLookupEnricher(hsqlDbPoolConfig, new MetaDataProviderFactory().create(hsqlDbPoolConfig))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  test("DatabaseLookupEnricher#implementation with cache") {
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
    val serviceLogic = service.implementation(
      params = Map(CacheTTLParamName -> java.time.Duration.ofDays(1)),
      dependencies = Nil,
      finalState = Some(state)
    )
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
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )
  }

}
