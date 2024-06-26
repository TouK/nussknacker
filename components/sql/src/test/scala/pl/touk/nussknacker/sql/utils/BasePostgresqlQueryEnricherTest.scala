package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.ForAllTestContainer
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time.{Second, Seconds, Span}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.jdk.CollectionConverters._

trait BasePostgresqlQueryEnricherTest
    extends BaseDatabaseQueryEnricherTest
    with PatientScalaFutures
    with BeforeAndAfterAll
    with ForAllTestContainer
    with WithPostgresqlDB {

  val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

  val postgresqlDbPoolConfig: DBPoolConfig = DBPoolConfig(
    driverClassName = postgresqlConfigValues("driverClassName"),
    url = postgresqlConfigValues("url"),
    username = postgresqlConfigValues("username"),
    password = postgresqlConfigValues("password")
  )

  val dbEnricherConfig: Config = ConfigFactory
    .load()
    .withValue("name", ConfigValueFactory.fromAnyRef("db-enricher"))
    .withValue(
      "dbPool",
      ConfigValueFactory.fromMap(
        postgresqlConfigValues.asJava
      )
    )

  override def beforeAll(): Unit = {
    // TODO_PAWEL is it ok?
    service.open(LiteEngineRuntimeContextPreparer.noOp.prepare(jobData))
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    service.close()
    super.afterAll()
  }

}
