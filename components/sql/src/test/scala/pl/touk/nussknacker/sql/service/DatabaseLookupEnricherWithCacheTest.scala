package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.service.DatabaseLookupEnricher.KeyValueParamName
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.cacheTTLParamName
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import scala.concurrent.Await

class DatabaseLookupEnricherWithCacheTest extends BaseHsqlQueryEnricherTest {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

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
    val implementation = service.implementation(
      params = Params(
        Map(
          cacheTTLParamName -> java.time.Duration.ofDays(1),
          KeyValueParamName -> 1L
        )
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(service, state).display shouldBe "List[Record{ID: Integer, NAME: String}]"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = implementation.invoke(Context.withInitialId)
    val result2  = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )
  }

}
