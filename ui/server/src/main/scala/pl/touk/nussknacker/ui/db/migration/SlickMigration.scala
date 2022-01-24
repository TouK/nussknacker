package pl.touk.nussknacker.ui.db.migration

import java.io.PrintWriter
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection
import java.util.logging.Logger
import io.circe.Json

import javax.sql.DataSource
import org.flywaydb.core.api.migration.{BaseJavaMigration, Context}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.EspTables
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._

trait SlickMigration extends BaseJavaMigration {

  protected val profile: JdbcProfile

  import profile.api._

  def migrateActions: DBIOAction[Any, NoStream, _ <: Effect]

  override def migrate(context: Context): Unit = {
    val conn = context.getConnection
    val database = Database.forDataSource(new AlwaysUsingSameConnectionDataSource(conn), None, AsyncExecutor.default("Slick migration", 20))
    Await.result(database.run(migrateActions), 10 minute)
  }
}

trait ProcessJsonMigration extends SlickMigration with EspTables {

  import scala.concurrent.ExecutionContext.Implicits.global
  import slick.dbio.DBIOAction
  import profile.api._

  override def migrateActions = for {
    processes <- processVersionsTable.map(pe => (pe.id, pe.processId, pe.json)).filter(_._3.isDefined).result
    seqed <- DBIOAction.sequence(processes.map((updateOne _).tupled))
  } yield seqed

  private def updateOne(id: VersionId, processId: ProcessId, json: Option[String]) = processVersionsTable
    .filter(v => v.id === id && v.processId === processId)
    .map(_.json).update(json.map(prepareAndUpdateJson))

  private def prepareAndUpdateJson(json: String): String = {
    val jsonProcess = CirceUtil.decodeJsonUnsafe[Json](json, "invalid scenario")
    val updated = updateProcessJson(jsonProcess)
    updated.getOrElse(jsonProcess).noSpaces
  }

  def updateProcessJson(json: Json): Option[Json]
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
        method.invoke(conn, args: _*)
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
