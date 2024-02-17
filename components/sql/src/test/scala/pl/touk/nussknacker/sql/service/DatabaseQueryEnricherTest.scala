package pl.touk.nussknacker.sql.service

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
    val invoker = service.createRuntimeLogic(Map.empty, dependencies = Nil, Some(state))
    returnType(service, state).display shouldBe "List[Record{ID: Integer, NAME: String}]"
    val resultF = invoker.apply(Map("arg1" -> 1))
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = invoker.apply(Map("arg1" -> 1))
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
    val invoker = service.createRuntimeLogic(Map.empty, dependencies = Nil, Some(state))
    returnType(service, state).display shouldBe "Integer"
    val resultF = invoker.apply(Map("arg1" -> 1))
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[Integer]
    result shouldBe 1
    val queryResultSet = conn.prepareStatement("SELECT * FROM persons WHERE id = 1").executeQuery()
    queryResultSet.next()
    queryResultSet.getObject("name") shouldBe "Don"
  }

}
