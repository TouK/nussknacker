package pl.touk.esp.ui.db.migration

import java.io.PrintWriter
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import slick.driver.JdbcDriver

import scala.concurrent.Await
import scala.concurrent.duration._

trait SlickMigration extends JdbcMigration {

  protected val driver: JdbcDriver

  import driver.api._

  def migrateActions: DBIOAction[Any, NoStream, _ <: Effect]

  override final def migrate(conn: Connection) = {
    val database = Database.forDataSource(new AlwaysUsingSameConnectionDataSource(conn))
    Await.result(database.run(migrateActions), 10 minute)
  }

}

class AlwaysUsingSameConnectionDataSource(conn: Connection) extends DataSource {
  private val notClosingConnection = Proxy.newProxyInstance(
    ClassLoader.getSystemClassLoader,
    Array[Class[_]](classOf[Connection]),
    SuppressCloseHandler
  ).asInstanceOf[Connection]

  object SuppressCloseHandler extends InvocationHandler {
    override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
      if (method.getName != "close") {
        method.invoke(conn, args : _*)
      } else {
        null
      }
    }
  }

  override def getConnection: Connection = notClosingConnection
  override def getConnection(username: String, password: String): Connection = notClosingConnection
  override def unwrap[T](iface: Class[T]): T = conn.unwrap(iface)
  override def isWrapperFor(iface: Class[_]): Boolean = conn.isWrapperFor(iface)

  override def setLogWriter(out: PrintWriter): Unit = ???
  override def getLoginTimeout: Int = ???
  override def setLoginTimeout(seconds: Int): Unit = ???
  override def getParentLogger: Logger = ???
  override def getLogWriter: PrintWriter = ???
}