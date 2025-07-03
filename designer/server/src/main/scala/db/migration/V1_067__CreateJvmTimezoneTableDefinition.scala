package db.migration

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.db.migration.SlickMigration

import java.time.ZoneId
import scala.concurrent.ExecutionContext.Implicits.global

// Information about current JVM ZoneId.systemDefault() is needed by the next migration and must be available on SQL level
// (it migrates some columns representing LocalDateTime without timezone information to UTC)
// The jvm_timezone table is used and then dropped by the next migration.
trait V1_067__CreateJvmTimezoneTableDefinition extends SlickMigration with LazyLogging {

  import profile.apiWithEnforcedSchema._

  private val zoneId: ZoneId = ZoneId.systemDefault()

  private class JvmTimezoneTable(tag: Tag) extends TableWithSchema[String](tag, "jvm_timezone") {
    def zoneId = column[String]("zone_id", O.PrimaryKey)
    def *      = zoneId
  }

  private val jvmTimezoneTable = TableQuery[JvmTimezoneTable]

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] = {
    logger.info(s"Starting migration V1_067__CreateJvmTimezoneTable with JVM timezone = $zoneId")
    for {
      _ <- jvmTimezoneTable.schema.create
      _ <- jvmTimezoneTable += zoneId.getId
    } yield ()
  }

}
