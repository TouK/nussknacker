package pl.touk.nussknacker.ui.db

import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

import org.flywaydb.core.Flyway
import slick.jdbc.JdbcBackend

class DatabaseInitializer(db: JdbcBackend.Database) {
  def initDatabase(): JdbcBackend.Database = {
    migrateIfNeeded(db)
    db
  }

  private def migrateIfNeeded(db: JdbcBackend.Database) = {
    val flyway = new Flyway()
    flyway.setDataSource(new DatabaseDataSource(db))
    flyway.setBaselineOnMigrate(true)
    flyway.migrate()
  }
}

class DatabaseDataSource(db: JdbcBackend.Database) extends DataSource {
  private val conn = db.createSession().conn

  override def getConnection: Connection = conn
  override def getConnection(username: String, password: String): Connection = conn
  override def unwrap[T](iface: Class[T]): T = conn.unwrap(iface)
  override def isWrapperFor(iface: Class[_]): Boolean = conn.isWrapperFor(iface)

  override def setLogWriter(out: PrintWriter): Unit = ???
  override def getLoginTimeout: Int = ???
  override def setLoginTimeout(seconds: Int): Unit = ???
  override def getParentLogger: Logger = ???
  override def getLogWriter: PrintWriter = ???
}