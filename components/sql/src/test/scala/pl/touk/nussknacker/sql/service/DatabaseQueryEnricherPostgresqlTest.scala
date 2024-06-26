package pl.touk.nussknacker.sql.service

import org.scalatest.BeforeAndAfterEach
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.sql.db.query.{ResultSetStrategy, UpdateResultStrategy}
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.{BaseHsqlQueryEnricherTest, BasePostgresqlQueryEnricherTest}

import scala.concurrent.Await

class DatabaseQueryEnricherPostgresqlTest extends BasePostgresqlQueryEnricherTest with BeforeAndAfterEach {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

  override val service =
    new DatabaseQueryEnricher(postgresqlDbPoolConfig, new MetaDataProviderFactory().create(postgresqlDbPoolConfig))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  override protected def afterEach(): Unit = {
    val cleanupStatements = List(
      "TRUNCATE TABLE persons;",
      "INSERT INTO persons (id, name) VALUES (1, 'John')"
    )
    cleanupStatements.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  test("DatabaseQueryEnricherPostgresqlTest#implementation without cache") {
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
          DatabaseQueryEnricher.cacheTTLParamName -> null,
          ParameterName("arg1")                   -> 1
        )
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(service, state).display shouldBe "List[Record{id: Integer, name: String}]"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("name" -> "John", "id" -> 1))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = implementation.invoke(Context.withInitialId.withVariables(Map("arg1" -> 1)))
    val result2  = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("name" -> "Alex", "id" -> 1))
    )
  }

  test(
    "DatabaseQueryEnricherPostgresqlTest#implementation without cache and with mixed lowercase and uppercase characters"
  ) {
    val query = "select iD, NaMe from persons where id = ?"
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
          DatabaseQueryEnricher.cacheTTLParamName -> null,
          ParameterName("arg1")                   -> 1
        )
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(service, state).display shouldBe "List[Record{id: Integer, name: String}]"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("name" -> "John", "id" -> 1))
    )

    conn.prepareStatement("UPDATE persons SET name = 'Alex' WHERE id = 1").execute()
    val resultF2 = implementation.invoke(Context.withInitialId.withVariables(Map("arg1" -> 1)))
    val result2  = Await.result(resultF2, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result2 shouldBe List(
      TypedMap(Map("name" -> "Alex", "id" -> 1))
    )
  }

  test("DatabaseQueryEnricherPostgresqlTest#implementation update query") {
    val query = "UPDATE persons SET name = 'Don' where id = ?"
    val st    = conn.prepareStatement(query)
    st.close()
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(Nil),
      strategy = UpdateResultStrategy
    )
    val implementation = service.implementation(
      params = Params(
        Map(
          DatabaseQueryEnricher.cacheTTLParamName -> null,
          ParameterName("arg1")                   -> 1
        )
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(service, state).display shouldBe "Integer"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[Integer]
    result shouldBe 1
    val queryResultSet = conn.prepareStatement("SELECT * FROM persons WHERE id = 1").executeQuery()
    queryResultSet.next()
    queryResultSet.getObject("name") shouldBe "Don"
  }

}
