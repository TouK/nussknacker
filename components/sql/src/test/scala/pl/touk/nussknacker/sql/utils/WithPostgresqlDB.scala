package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName

import java.sql.{Connection, DriverManager}
import scala.jdk.CollectionConverters._

trait WithPostgresqlDB {
  self: BeforeAndAfterAll with ForAllTestContainer =>

  var conn: Connection = _

  override val container: PostgreSQLContainer = {
    val container = PostgreSQLContainer(DockerImageName.parse("postgres:13"))
    container.container.setPortBindings(List("5432:5432").asJava)
    container
  }

  private val driverClassName = "org.postgresql.Driver"
  private val username        = container.username
  private val password        = container.password
  // this url can be read as container.jdbcUrl when service is started, but it is hard to postpone this step until this service is started
  private val url = "jdbc:postgresql://localhost:5432/test?loggerLevel=OFF"

  val postgresqlConfigValues: Map[String, String] = Map(
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
