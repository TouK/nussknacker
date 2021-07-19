package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.query.ResultSetStrategy
import pl.touk.nussknacker.sql.db.schema.TableDefinition
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.CacheTTLParamName
import pl.touk.nussknacker.sql.utils.BaseDatabaseQueryEnricherTest

import scala.concurrent.Await

class DatabaseLookupEnricherWithCacheTest extends BaseDatabaseQueryEnricherTest {

  import scala.collection.JavaConverters._
  import scala.concurrent.duration._

  override val service = new DatabaseLookupEnricher(dbConf)

  override val prepareDbDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  test("DatabaseLookupEnricher#implementation with cache") {
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
    val invoker = service.implementation(
      params = Map(CacheTTLParamName -> java.time.Duration.ofDays(1)),
      dependencies = Nil,
      finalState = Some(state)
    )
    invoker.returnType.display shouldBe "List[{ID: Integer, NAME: String}]"
    val resultF = invoker.invokeService(Map(DatabaseLookupEnricher.KeyValueParamName -> 1L))
    val result = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = invoker.invokeService(Map(DatabaseLookupEnricher.KeyValueParamName -> 1L))
    val result2 = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )
  }
}
