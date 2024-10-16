package pl.touk.nussknacker.sql.utils

import org.hsqldb.jdbcDriver
import org.scalatest.BeforeAndAfterAll

import java.sql.{Connection, DriverManager}
import java.util.UUID

trait WithHsqlDB {
  self: BeforeAndAfterAll =>

  var conn: Connection = _

  val dbName: String = UUID.randomUUID().toString

  private val driverClassName = "org.hsqldb.jdbc.JDBCDriver"
  private val username        = "SA"
  private val url             = s"jdbc:hsqldb:mem:$dbName;hsqldb.tx=mvcc"
  private val password        = ""

  val hsqlConfigValues: Map[String, String] = Map(
    "driverClassName" -> driverClassName,
    "username"        -> username,
    "password"        -> password,
    "url"             -> url
  )

  def prepareHsqlDDLs: List[String]

  override protected def beforeAll(): Unit = {
    // DriverManager initializes drivers once per JVM start thus drivers loaded later are skipped.
    // We must ensue that they are load manually
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
