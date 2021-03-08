package pl.touk.nussknacker.extensions.db

import java.sql.Connection

trait WithDBConnectionPool {

  def connectionPool: DBConnectionPool

  def withConnection[T](f: Connection => T): T = {
    val conn = connectionPool.getConnection
    try f(conn) finally conn.close()
  }
}
