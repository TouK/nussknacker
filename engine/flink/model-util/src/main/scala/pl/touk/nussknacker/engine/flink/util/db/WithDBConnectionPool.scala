package pl.touk.nussknacker.engine.flink.util.db

import org.apache.commons.dbcp2.BasicDataSource

import java.sql.Connection

trait WithDBConnectionPool {

  def connectionPool: BasicDataSource

  def withConnection[T](f: Connection => T): T = {
    val conn = connectionPool.getConnection
    try f(conn) finally conn.close()
  }
}
