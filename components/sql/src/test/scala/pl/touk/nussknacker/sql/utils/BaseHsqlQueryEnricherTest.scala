package pl.touk.nussknacker.sql.utils

import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

trait BaseHsqlQueryEnricherTest extends BaseDatabaseQueryEnricherTest with WithHsqlDB {

  val hsqlDbPoolConfig: DBPoolConfig = DBPoolConfig(
    driverClassName = hsqlConfigValues("driverClassName"),
    url = hsqlConfigValues("url"),
    username = hsqlConfigValues("username"),
    password = hsqlConfigValues("password")
  )

  override def beforeAll(): Unit = {
    service.open(LiteEngineRuntimeContextPreparer.noOp.prepare(jobData))
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    service.close()
    super.afterAll()
  }

}
