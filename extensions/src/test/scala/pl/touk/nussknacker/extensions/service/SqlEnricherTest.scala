package pl.touk.nussknacker.extensions.service

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{ContextId, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{QueryServiceInvocationCollector, ServiceInvocationCollector}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.extensions.db.pool.HikariDataSourceFactory
import pl.touk.nussknacker.extensions.db.query.ResultSetStrategy
import pl.touk.nussknacker.extensions.db.schema.TableDefinition
import pl.touk.nussknacker.extensions.utils.WithDB

import scala.concurrent.{Await, ExecutionContext}

class SqlEnricherTest extends FunSuite
  with Matchers with BeforeAndAfterAll with WithDB {
  import scala.collection.JavaConverters._
  import scala.concurrent.duration._

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private implicit val contextId: ContextId = ContextId("")
  private implicit val metaData: MetaData = MetaData("", StreamMetaData())
  private implicit val collector: ServiceInvocationCollector = QueryServiceInvocationCollector(runIdOpt = None, serviceName = "")

  private val service = new SqlEnricher(HikariDataSourceFactory(dbConf))

  override val prepareDbDDLs: List[String] = List(
  "CREATE TABLE persons (id INT, name VARCHAR(40));",
  "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  test("#implementation") {
    val query = "select * from persons where id = ?"
    val st = conn.prepareStatement(query)
    val meta = st.getMetaData
    st.close()
    val state = SqlEnricher.TransformationState(
      query = query,
      argsCount = 1,
      tableDef = TableDefinition(meta),
      strategy = ResultSetStrategy
    )
    val invoker = service.implementation(Map.empty, dependencies = Nil, Some(state))
    invoker.returnType.display shouldBe "List[{ID: Integer, NAME: String}]"
    val resultF = invoker.invokeService(Map("arg1" -> 1))
    val result = Await.result(resultF, 5 seconds).asInstanceOf[java.util.List[TypedMap]].asScala.toList
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )
  }
}
