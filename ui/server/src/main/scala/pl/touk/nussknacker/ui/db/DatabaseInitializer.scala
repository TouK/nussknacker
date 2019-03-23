package pl.touk.nussknacker.ui.db

import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger

import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.hsqldb.server.Server
import slick.jdbc.{HsqldbProfile, JdbcBackend, PostgresProfile}

object DatabaseServer {
  private[this] val i = 0

  def apply(config: DatabaseServer.Config): Server = {
    val server = new Server()
    server.setSilent(true)
    server.setDatabasePath(i, s"file:${config.dbFilePath};user=${config.user};password=${config.password};sql.syntax_ora=true")
    server.setDatabaseName(i, config.dbName)
    server.setSilent(true)
    config.port.foreach(server.setPort)
    server
  }
  case class Config(dbFilePath:String, dbName:String, user:String, password:String, port:Option[Int])
}

class DatabaseInitializer(dbConfig: DbConfig) {
  def initDatabase(): Unit = {
    migrateIfNeeded(dbConfig)
  }

  private def migrateIfNeeded(dbConfig: DbConfig) = {
    val flyway = new Flyway()
    flyway.setDataSource(new DatabaseDataSource(dbConfig.db))
    flyway.setBaselineOnMigrate(true)
    dbConfig.driver match {
      case HsqldbProfile => flyway.setLocations("db/migration/hsql", "db/migration/common")
      case PostgresProfile => flyway.setLocations("db/migration/postgres", "db/migration/common")
    }
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