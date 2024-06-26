package pl.touk.nussknacker.sql.service

import org.scalatest.BeforeAndAfterEach
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.sql.db.query.{ResultSetStrategy, UpdateResultStrategy}
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.{BaseOracleQueryEnricherTest, BasePostgresqlQueryEnricherTest}

import scala.concurrent.Await
import scala.math.BigDecimal.javaBigDecimal2bigDecimal

class DatabaseQueryEnricherOracleTest extends BaseOracleQueryEnricherTest with BeforeAndAfterEach {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

  override val service =
    new DatabaseQueryEnricher(oracleDbPoolConfig, new MetaDataProviderFactory().create(oracleDbPoolConfig))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id NUMBER(15), name VARCHAR2(40))",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  override protected def afterEach(): Unit = {
    val cleanupStatements = List(
      "TRUNCATE TABLE persons",
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
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(meta),
      strategy = ResultSetStrategy
    )
    st.close()
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
    returnType(service, state).display shouldBe "List[Record{ID: BigDecimal, NAME: String}]"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result(0).get("ID").asInstanceOf[java.math.BigDecimal].toInt shouldBe 1
    result(0).get("NAME").asInstanceOf[String] shouldBe "John"
  }

  test(
    "DatabaseQueryEnricherPostgresqlTest#implementation without cache and with mixed lowercase and uppercase characters"
  ) {
    val query = "select Id, nAmE from persons where id = ?"
    val st    = conn.prepareStatement(query)
    val meta  = st.getMetaData
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(meta),
      strategy = ResultSetStrategy
    )
    st.close()
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
    returnType(service, state).display shouldBe "List[Record{ID: BigDecimal, NAME: String}]"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result(0).get("ID").asInstanceOf[java.math.BigDecimal].toInt shouldBe 1
    result(0).get("NAME").asInstanceOf[String] shouldBe "John"
  }

  test("DatabaseQueryEnricherPostgresqlTest#implementation update query") {
    val query = "UPDATE persons SET name = 'Don' where id = ?"
    val st    = conn.prepareStatement(query)
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(Nil),
      strategy = UpdateResultStrategy
    )
    st.close()
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
