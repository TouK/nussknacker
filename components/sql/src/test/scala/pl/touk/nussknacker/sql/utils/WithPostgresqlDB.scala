package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName

import java.sql.{Connection, DriverManager}

trait WithPostgresqlDB {
  self: BeforeAndAfterAll with ForAllTestContainer =>

  var conn: Connection = _

  override val container: PostgreSQLContainer = {
    val container = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
    container.container.withUrlParam("loggerLevel", "OFF")
    container
  }

  private val driverClassName = "org.postgresql.Driver"
  private val username        = container.username
  private val password        = container.password
  private lazy val url        = container.jdbcUrl

  lazy val postgresqlConfigValues: Map[String, String] = Map(
    "driverClassName" -> driverClassName,
    "username"        -> username,
    "password"        -> password,
    "url"             -> url
  )

  def preparePostgresqlDDLs: List[String]

  override protected def beforeAll(): Unit = {
    conn = DriverManager.getConnection(url, username, password)
    preparePostgresqlDDLs.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  override protected def afterAll(): Unit = {
    Option(conn).foreach(_.close())
  }

}
