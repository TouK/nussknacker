package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.ForAllTestContainer
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.time.{Second, Seconds, Span}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.jdk.CollectionConverters._

trait BaseOracleQueryEnricherTest
    extends BaseDatabaseQueryEnricherTest
    with PatientScalaFutures
    with BeforeAndAfterAll
    with ForAllTestContainer
    with WithOracleDB {

  val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

  val oracleDbPoolConfig: DBPoolConfig = DBPoolConfig(
    driverClassName = oracleConfigValues("driverClassName"),
    url = oracleConfigValues("url"),
    username = oracleConfigValues("username"),
    password = oracleConfigValues("password")
  )

  val dbEnricherConfig: Config = ConfigFactory
    .load()
    .withValue("name", ConfigValueFactory.fromAnyRef("db-enricher"))
    .withValue(
      "dbPool",
      ConfigValueFactory.fromMap(
        oracleConfigValues.asJava
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
