package pl.touk.nussknacker.sql.utils

import org.scalatest.BeforeAndAfterAll
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig

import java.sql.{Connection, DriverManager}
import java.util.UUID

trait WithDB { self: BeforeAndAfterAll =>

  var conn: Connection = _

  val dbName: String = UUID.randomUUID().toString

  val dbConf: DBPoolConfig = DBPoolConfig(
    driverClassName = "org.hsqldb.jdbc.JDBCDriver",
    url = s"jdbc:hsqldb:mem:$dbName",
    username = "SA",
    password = "")

  def prepareDbDDLs: List[String]

  override def beforeAll(): Unit = {
    conn = DriverManager.getConnection(dbConf.url, dbConf.username, dbConf.password)
    prepareDbDDLs.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute() finally ddlStatement.close()
    }
  }

  override def afterAll(): Unit = {
    Option(conn).foreach(_.close())
  }
}
