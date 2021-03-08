package pl.touk.nussknacker.extensions.db

import java.sql.{Connection, PreparedStatement}
import javax.sql.DataSource

trait WithDBConnectionPool {

  def dataSource: DataSource

  def withConnection[T](query: String)(f: PreparedStatement => T): T = {
    withConnection { conn =>
      val statement = conn.prepareStatement(query)
      try f(statement) finally statement.close()
    }
  }

  def withConnection[T](f: Connection => T): T = {
    val conn = dataSource.getConnection
    try f(conn) finally conn.close()
  }
}
