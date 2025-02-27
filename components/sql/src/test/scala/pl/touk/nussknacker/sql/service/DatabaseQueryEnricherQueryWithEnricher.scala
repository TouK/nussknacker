package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.parameter.ParameterName
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
      argsCount = parameters.size,
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
    val resultFuture = implementation.invoke(Context.withInitialId)
    Await.result(resultFuture, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
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
    returnType(databaseQueryEnricher, state).display shouldBe "Integer"
    val resultFuture = implementation.invoke(Context.withInitialId)
    val result       = Await.result(resultFuture, 5 seconds).asInstanceOf[Integer]
    result shouldBe 1
  }

}
