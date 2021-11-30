package pl.touk.nussknacker.sql.utils

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

import scala.collection.JavaConverters._

trait BaseHsqlQueryEnricherTest extends BaseDatabaseQueryEnricherTest with WithHsqlDB {

  val hsqlDbPoolConfig: DBPoolConfig = DBPoolConfig(
    driverClassName = hsqlConfigValues("driverClassName"),
    url = hsqlConfigValues("url"),
    username = hsqlConfigValues("username"),
    password = hsqlConfigValues("password")
  )

  val dbEnricherConfig: Config = ConfigFactory.load()
    .withValue("name", ConfigValueFactory.fromAnyRef("db-enricher"))
    .withValue("dbPool", ConfigValueFactory.fromMap(
      hsqlConfigValues.asJava
    ))

  override def beforeAll(): Unit = {
    service.open(LiteEngineRuntimeContextPreparer.noOp.prepare(jobData))
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    service.close()
    super.afterAll()
  }
}
