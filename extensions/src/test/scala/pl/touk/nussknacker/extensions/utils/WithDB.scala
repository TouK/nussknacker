package pl.touk.nussknacker.extensions.utils

import org.scalatest.BeforeAndAfterAll
import pl.touk.nussknacker.extensions.db.pool.DBPoolConfig

import java.sql.{Connection, DriverManager}

trait WithDB { self: BeforeAndAfterAll =>

  var conn: Connection = _

  val dbConf = DBPoolConfig(
    driverClassName = "org.hsqldb.jdbc.JDBCDriver",
    url = "jdbc:hsqldb:mem:testdb",
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
