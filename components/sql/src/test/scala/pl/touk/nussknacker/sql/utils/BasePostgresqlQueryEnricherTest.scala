package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.ForAllTestContainer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time.{Second, Seconds, Span}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.test.PatientScalaFutures

trait BasePostgresqlQueryEnricherTest
    extends BaseDatabaseQueryEnricherTest
    with PatientScalaFutures
    with BeforeAndAfterAll
    with ForAllTestContainer
    with WithPostgresqlDB {

  val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

  lazy val postgresqlDbPoolConfig: DBPoolConfig = DBPoolConfig(
    driverClassName = postgresqlConfigValues("driverClassName"),
    url = postgresqlConfigValues("url"),
    username = postgresqlConfigValues("username"),
    password = postgresqlConfigValues("password")
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
