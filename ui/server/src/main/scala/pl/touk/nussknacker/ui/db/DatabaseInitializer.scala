package pl.touk.nussknacker.ui.db

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.jdbc.DriverDataSource.DriverType
import org.hsqldb.server.Server

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
  case class Config(dbFilePath:String, dbName:String, user:String, password:String, port:Option[Int], enabled: Option[Boolean])
}

object DatabaseInitializer {

  def initDatabase(path: String, config: Config): Unit = {
    import net.ceedubs.ficus.Ficus._
    val configDb = config.getConfig(path)
    DatabaseInitializer.initDatabase(
      configDb.as[String]("url"),
      configDb.as[String]("user"),
      configDb.as[String]("password"),
      configDb.getAs[String]("schema"))
  }

  def initDatabase(url: String, user: String, password: String, schema: Option[String] = None): Unit =
    Flyway
      .configure()
      .locations(
        (url match {
          case hsqldbUrl if DriverType.HSQL.matches(hsqldbUrl) => Array("db/migration/hsql", "db/migration/common")
          case postgresqlUrl if DriverType.POSTGRESQL.matches(postgresqlUrl) => Array("db/migration/postgres", "db/migration/common")
          case _ => throw new IllegalArgumentException(s"Unsuported database url: $url . Use either PostgreSQL or HSQLDB.")
        }): _*
      )
      .dataSource(url, user, password)
      .schemas(schema.toArray: _*)
      .baselineOnMigrate(true)
      .load().migrate()
}
