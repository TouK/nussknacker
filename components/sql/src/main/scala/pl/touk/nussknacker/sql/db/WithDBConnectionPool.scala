package pl.touk.nussknacker.sql.db

import java.sql.{Connection, PreparedStatement}

trait WithDBConnectionPool {

  val getConnection: () => Connection

  def withConnection[T](query: String)(f: PreparedStatement => T): T = {
    withConnection { conn =>
      val statement = conn.prepareStatement(query)
      try f(statement) finally statement.close()
    }
  }

  private def withConnection[T](f: Connection => T): T = {
    val conn = getConnection()
    try f(conn) finally conn.close()
  }
}
