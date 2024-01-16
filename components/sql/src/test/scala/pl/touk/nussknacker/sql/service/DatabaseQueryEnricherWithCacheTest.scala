package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.ServiceLogic.{ParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.CacheTTLParamName
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import scala.concurrent.Await

class DatabaseQueryEnricherWithCacheTest extends BaseHsqlQueryEnricherTest {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  override val service =
    new DatabaseQueryEnricher(hsqlDbPoolConfig, new MetaDataProviderFactory().create(hsqlDbPoolConfig))

  test("DatabaseQueryEnricher#implementation with cache") {
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
    val serviceLogic = service.runLogic(
      params = Map(CacheTTLParamName -> java.time.Duration.ofDays(1)),
      dependencies = Nil,
      finalState = Some(state)
    )
    val paramsEvaluator = ParamsEvaluator.create(context, _ => Map("arg1" -> 1))
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

    // it's not production behaviour - we only close service to make sure DB connection is closed, and prove that
    // the value is populated from cache.
    service.close()

    val resultF3 = serviceLogic.run(paramsEvaluator)
    val result3  = Await.result(resultF3, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result3 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )
  }

}
