package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.{ForAllTestContainer, OracleContainer}
import org.hsqldb.jdbcDriver
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName

import java.sql.{Connection, DriverManager}
import java.util.UUID
import scala.jdk.CollectionConverters._

trait WithOracleDB {
  self: BeforeAndAfterAll with ForAllTestContainer =>

  var conn: Connection = _

  override val container: OracleContainer =
    new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart"))

  {
    container.container.setPortBindings(List("1521:1521").asJava)
  }

  val dbName: String = UUID.randomUUID().toString

  private val driverClassName = "oracle.jdbc.driver.OracleDriver"
  private val username        = container.username
  private val password        = container.password
  // this url be read as container.jdbcUrl when service is started, but it is hard to postpone this step until it is started
  private val url = "jdbc:oracle:thin:@localhost:1521/xepdb1"

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
