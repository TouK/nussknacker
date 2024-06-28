package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.query.{ResultSetStrategy, UpdateResultStrategy}
import pl.touk.nussknacker.sql.db.schema.TableDefinition
import pl.touk.nussknacker.sql.utils.BaseDatabaseQueryEnricherTest

import java.sql.Connection
import scala.concurrent.Await

trait DatabaseQueryEnricherQueryWithEnricher extends BaseDatabaseQueryEnricherTest {

  import scala.concurrent.duration._
  import scala.jdk.CollectionConverters._

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
    val invoker = databaseQueryEnricher.implementation(Map.empty, dependencies = Nil, Some(state))
    returnType(databaseQueryEnricher, state).display shouldBe expectedDisplayType
    val resultF = invoker.invokeService(parameters)
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
    val invoker = databaseQueryEnricher.implementation(Map.empty, dependencies = Nil, Some(state))
    returnType(databaseQueryEnricher, state).display shouldBe "Integer"
    val resultF = invoker.invokeService(parameters)
    Await.result(resultF, 5 seconds).asInstanceOf[Integer]
  }

}
