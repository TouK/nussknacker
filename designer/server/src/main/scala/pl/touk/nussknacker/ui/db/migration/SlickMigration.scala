package pl.touk.nussknacker.ui.db.migration

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import org.flywaydb.core.api.migration.{BaseJavaMigration, Context}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.NuTables
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration._

trait SlickMigration extends BaseJavaMigration {

  protected val profile: JdbcProfile

  import profile.api._

  protected def migrateActions: DBIOAction[Any, NoStream, _ <: Effect]

  override def migrate(context: Context): Unit = {
    val conn = context.getConnection
    val database = Database.forDataSource(
      new AlwaysUsingSameConnectionDataSource(conn),
      None,
      AsyncExecutor.default("Slick migration", 20)
    )
    Await.result(database.run(migrateActions), Duration.Inf)
  }

}

trait ProcessJsonMigration extends SlickMigration with NuTables with LazyLogging {

  import profile.api._
  import slick.dbio.DBIOAction

  import scala.concurrent.ExecutionContext.Implicits.global

  override protected def migrateActions
      : DBIOAction[Seq[Int], NoStream, Effect.Read with Effect.Read with Effect.Write] = {
    logger.error("Migration")
    for {
      allVersionIds <- processVersionsTableWithUnit.map(pve => (pve.id, pve.processId)).result
      updated <- DBIOAction.sequence(allVersionIds.zipWithIndex.map { case ((id, processId), scenarioIndex) =>
        updateOne(id, processId, scenarioIndex + 1, scenariosCount = allVersionIds.size)
      })
    } yield updated
  }

  private def updateOne(
      id: VersionId,
      processId: ProcessId,
      scenarioNo: Int,
      scenariosCount: Int
  ): DBIOAction[Int, NoStream, Effect.Read with Effect.Write] = {
    for {
      processJson <- processVersionsTable.filter(v => v.id === id && v.processId === processId).map(_.json).result.head
      updatedJson <- processVersionsTable
        .filter(v => v.id === id && v.processId === processId)
        .map(_.json)
        .update {
          logger.trace("Migrate scenario ({}/{}), id: {}, version id: {}", scenarioNo, scenariosCount, processId, id)
          prepareAndUpdateJson(processJson)
        }
    } yield updatedJson
  }

  private def prepareAndUpdateJson(json: String): String = {
    val jsonProcess = CirceUtil.decodeJsonUnsafe[Json](json, "invalid scenario")
    val updated     = updateProcessJson(jsonProcess)
    updated.getOrElse(jsonProcess).noSpaces
  }

  /**
   * Note on transactions - in case of failure:
   * <ul>
   * <li>if we want to roll back the transaction and stop the application - the implementation should throw an exception</li>
   * <li>if we want to continue and fall back to previous json - the implementation should return None</li>
   * </ul>
   */
  def updateProcessJson(json: Json): Option[Json]

}
