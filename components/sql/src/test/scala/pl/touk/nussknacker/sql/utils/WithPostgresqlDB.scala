package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import org.hsqldb.jdbcDriver
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName
import scala.jdk.CollectionConverters._

import java.sql.{Connection, DriverManager}
import java.util.UUID

trait WithPostgresqlDB {
  self: BeforeAndAfterAll with ForAllTestContainer =>

  var conn: Connection = _

  override val container: PostgreSQLContainer =
    PostgreSQLContainer(DockerImageName.parse("postgres:11.2"))

  {
    container.container.setPortBindings(List("5432:5432").asJava)
  }

  val dbName: String = UUID.randomUUID().toString

  private val driverClassName = "org.postgresql.Driver"
  private val username        = container.username
  private val password        = container.password
  // this url be read as container.jdbcUrl when service is started, but it is hard to postpone this step until it is started
  private val url = "jdbc:postgresql://localhost:5432/test?loggerLevel=OFF"

  val postgresqlConfigValues: Map[String, String] = Map(
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
