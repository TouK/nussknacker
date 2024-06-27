package pl.touk.nussknacker.sql.service

import org.scalatest.BeforeAndAfterEach
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.sql.db.query.{ResultSetStrategy, UpdateResultStrategy}
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BasePostgresqlQueryEnricherTest

import java.sql.Connection
import scala.concurrent.Await

class DatabaseQueryEnricherPostgresqlTest extends BasePostgresqlQueryEnricherTest with BeforeAndAfterEach {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

  override val service =
    new DatabaseQueryEnricher(postgresqlDbPoolConfig, new MetaDataProviderFactory().create(postgresqlDbPoolConfig))

  override val preparePostgresqlDDLs: List[String] = List(
    "CREATE TABLE people (id INT, name VARCHAR(40));",
    "INSERT INTO people (id, name) VALUES (1, 'John')"
  )

  override protected def afterEach(): Unit = {
    val cleanupStatements = List(
      "TRUNCATE TABLE people;",
      "INSERT INTO people (id, name) VALUES (1, 'John')"
    )
    cleanupStatements.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  test("DatabaseQueryEnricherPostgresqlTest#implementation without cache") {
    val result = queryWithEnricher(
      "select * from people where id = ?",
      Map("arg1" -> 1.asInstanceOf[AnyRef]),
      conn,
      service,
      "List[Record{id: Integer, name: String}]"
    )
    result shouldBe List(
      TypedMap(Map("name" -> "John", "id" -> 1))
    )
  }

  def queryWithEnricher(
      query: String,
      parameters: Map[String, AnyRef],
      connection: Connection,
      databaseQueryEnricher: DatabaseQueryEnricher,
      expectedDisplayType: String
  ): List[TypedMap] = {
    val st   = connection.prepareStatement(query)
    val meta = st.getMetaData
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(meta),
      strategy = ResultSetStrategy
    )
    st.close()
    val implementation = databaseQueryEnricher.implementation(
      params = Params(
        parameters.map { case (k, v) => (ParameterName(k), v) }
          + (DatabaseQueryEnricher.cacheTTLParamName -> null)
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(databaseQueryEnricher, state).display shouldBe expectedDisplayType
    val resultF = implementation.invoke(Context.withInitialId)
    Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
  }

  def updateWithEnricher(
      query: String,
      connection: Connection,
      parameters: Map[String, AnyRef],
      databaseQueryEnricher: DatabaseQueryEnricher
  ): Unit = {
    val st = connection.prepareStatement(query)
    st.close()
    val state = DatabaseQueryEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(Nil),
      strategy = UpdateResultStrategy
    )
    val implementation = databaseQueryEnricher.implementation(
      params = Params(
        parameters.map { case (k, v) => (ParameterName(k), v) }
          + (DatabaseQueryEnricher.cacheTTLParamName -> null)
      ),
      dependencies = Nil,
      finalState = Some(state)
    )
    returnType(service, state).display shouldBe "Integer"
    val resultF = implementation.invoke(Context.withInitialId)
    val result  = Await.result(resultF, 5 seconds).asInstanceOf[Integer]
    result shouldBe 1
  }

  test(
    "DatabaseQueryEnricherPostgresqlTest#implementation without cache and with mixed lowercase and uppercase characters"
  ) {
    val result = queryWithEnricher(
      "select iD, NaMe from people where id = ?",
      Map("arg1" -> 1.asInstanceOf[AnyRef]),
      conn,
      service,
      "List[Record{id: Integer, name: String}]"
    )
    result shouldBe List(
      TypedMap(Map("name" -> "John", "id" -> 1))
    )
  }

  test("DatabaseQueryEnricherPostgresqlTest#implementation update query") {
    val query = "UPDATE people SET name = 'Don' where id = ?"
    updateWithEnricher(query, conn, Map("arg1" -> 1.asInstanceOf[AnyRef]), service)

    val queryResultSet = conn.prepareStatement("SELECT * FROM people WHERE id = 1").executeQuery()
    queryResultSet.next()
    queryResultSet.getObject("name") shouldBe "Don"
  }

}
