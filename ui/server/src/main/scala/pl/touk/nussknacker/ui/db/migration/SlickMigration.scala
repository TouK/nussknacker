package pl.touk.nussknacker.ui.db.migration

import java.io.PrintWriter
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

import argonaut.{Json, Parse}
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._

trait SlickMigration extends JdbcMigration {

  protected val profile: JdbcProfile

  import profile.api._

  def migrateActions: DBIOAction[Any, NoStream, _ <: Effect]

  override final def migrate(conn: Connection) = {
    val database = Database.forDataSource(new AlwaysUsingSameConnectionDataSource(conn))
    Await.result(database.run(migrateActions), 10 minute)
  }

}

trait ProcessJsonMigration extends SlickMigration {

  import scala.concurrent.ExecutionContext.Implicits.global
  import slick.dbio.DBIOAction
  import profile.api._

  override def migrateActions = for {
    processes <- EspTables.processVersionsTable.filter(_.json.isDefined).result
    seqed <- DBIOAction.sequence(processes.map(updateOne))
  } yield seqed

  private def updateOne(process: ProcessVersionEntityData) = EspTables.processVersionsTable
          .filter(v => v.id === process.id && v.processId === process.processId)
          .map(_.json).update(process.json.map(prepareAndUpdateJson))

  private def prepareAndUpdateJson(json: String) : String = {
    val jsonProcess = Parse.parse(json).right.get
    val updated = updateProcessJson(jsonProcess)
    updated.getOrElse(jsonProcess).nospaces
  }

  def updateProcessJson(json: Json) : Option[Json]
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