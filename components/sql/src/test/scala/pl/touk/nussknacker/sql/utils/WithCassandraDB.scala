package pl.touk.nussknacker.sql.utils

import com.dimafeng.testcontainers.{CassandraContainer, Container, ForAllTestContainer}
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters._
import java.sql.{Connection, DriverManager}

trait WithCassandraDB {
  self: BeforeAndAfterAll with ForAllTestContainer =>

  var conn: Connection = _

  private val cassandraContainer = new CassandraContainer(Some(DockerImageName.parse("cassandra:5.0")))

  override val container: Container = cassandraContainer

  {
    cassandraContainer.cassandraContainer.setPortBindings(List("9042:9042").asJava)
  }

  private val driverClassName = "com.ing.data.cassandra.jdbc.CassandraDriver"
  private val username        = cassandraContainer.username
  private val password        = cassandraContainer.password
  // this will be used by database lookup enricher
  private val url = "jdbc:cassandra://localhost:9042/cycling?localdatacenter=datacenter1"
  // this will be used to prepare database for test
  private val startupUrl = "jdbc:cassandra://localhost:9042/system?localdatacenter=datacenter1"

  val cassandraSqlConfigValues: Map[String, String] = Map(
    "driverClassName" -> driverClassName,
    "username"        -> username,
    "password"        -> password,
    "url"             -> url
  )

  def prepareCassandraSqlDDLs: List[String]

  override protected def beforeAll(): Unit = {
    conn = DriverManager.getConnection(startupUrl, username, password)
    prepareCassandraSqlDDLs.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  override protected def afterAll(): Unit = {
    Option(conn).foreach(_.close())
  }

}
