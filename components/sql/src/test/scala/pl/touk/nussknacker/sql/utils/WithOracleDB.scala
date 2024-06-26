package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.{ForAllTestContainer, OracleContainer}
import org.hsqldb.jdbcDriver
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName

import java.sql.{Connection, DriverManager}
import java.util.UUID

trait WithOracleDB {
  self: BeforeAndAfterAll with ForAllTestContainer =>

  var conn: Connection = _

  override val container: OracleContainer =
    new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart"))

  {
    // TODO_PAWEL do it other way
    container.start()
  }

  val dbName: String = UUID.randomUUID().toString

  private val driverClassName = "oracle.jdbc.driver.OracleDriver"
  private val username        = container.username
  private val password        = container.password
  private val url             = container.jdbcUrl

  val oracleConfigValues: Map[String, String] = Map(
    "driverClassName" -> driverClassName,
    "username"        -> username,
    "password"        -> password,
    "url"             -> url
  )

  def prepareHsqlDDLs: List[String]

  override protected def beforeAll(): Unit = {
    // DriverManager initializes drivers once per JVM start thus drivers loaded later are skipped.
    // We must ensue that they are load manually
    // TODO_PAWEL jaka klasa?
    DriverManager.registerDriver(new jdbcDriver())
    conn = DriverManager.getConnection(url, username, password)
    prepareHsqlDDLs.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  override protected def afterAll(): Unit = {
    Option(conn).foreach(_.close())
  }

}
