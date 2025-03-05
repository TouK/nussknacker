package pl.touk.nussknacker.sql.utils.ignite

import org.apache.ignite.{Ignite, IgniteJdbcThinDriver, Ignition}
import org.apache.ignite.configuration.{ClientConnectorConfiguration, IgniteConfiguration}
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.test.AvailablePortFinder

import java.sql.{Connection, DriverManager}

trait WithIgniteDB extends BeforeAndAfterAll {
  self: Suite =>

  var ignitePort: Int  = AvailablePortFinder.findAvailablePorts(1).head
  var ignite: Ignite   = _
  var conn: Connection = _

  private val driverClassName = "org.apache.ignite.IgniteJdbcThinDriver"
  private val url             = s"jdbc:ignite:thin://127.0.0.1:${ignitePort}"
  private val username        = "ignite"
  private val password        = "ignite"

  val igniteDbConf: DBPoolConfig = DBPoolConfig(
    driverClassName = driverClassName,
    url = url,
    username = username,
    password = password
  )

  val igniteConfigValues: Map[String, String] = Map(
    "driverClassName" -> driverClassName,
    "username"        -> username,
    "password"        -> password,
    "url"             -> url
  )

  def prepareIgniteDDLs: List[String]

  override def beforeAll(): Unit = {
    super.beforeAll()
    // DriverManager initializes drivers once per JVM start thus drivers loaded later are skipped.
    // We must ensue that they are load manually
    DriverManager.registerDriver(new IgniteJdbcThinDriver())
    ignite = Ignition.getOrStart(
      new IgniteConfiguration()
        .setWorkDirectory("/tmp/")
        .setClientConnectorConfiguration(
          new ClientConnectorConfiguration()
            .setPort(ignitePort)
        )
    )

    conn = DriverManager.getConnection(url, username, password)
    prepareIgniteDDLs.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  override protected def afterAll(): Unit = {
    try {
      Ignition.stopAll(true)
    } finally {
      super.afterAll()
    }
  }

}
