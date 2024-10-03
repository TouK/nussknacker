package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_056__CreateScenarioActivitiesDefinition.ScenarioActivitiesDefinitions
import db.migration.V1_058__UpdateAndAddMissingScenarioActivitiesDefinition.Migration
import pl.touk.nussknacker.ui.db.entity.{ScenarioActivityEntityFactory, ScenarioActivityType}
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.ast.Library.JdbcFunction
import slick.jdbc.JdbcProfile
import slick.lifted.FunctionSymbolExtensionMethods.functionSymbolExtensionMethods
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

trait V1_058__UpdateAndAddMissingScenarioActivitiesDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] =
    new Migration(profile).migrate

}

object V1_058__UpdateAndAddMissingScenarioActivitiesDefinition extends LazyLogging {

  class Migration(val profile: JdbcProfile) extends ScenarioActivityEntityFactory {

    import profile.api._

    private val scenarioActivitiesDefinitions = new ScenarioActivitiesDefinitions(profile)
    private val processVersionsDefinitions    = new ProcessVersionsDefinitions(profile)

    def migrate: DBIOAction[Unit, NoStream, Effect.All] = for {
      // Insert SCENARIO_MODIFIED activity for each scenario version, that did not have activity until now
      _ <- insertScenarioModifiedActivityForEachVersionWithoutActivity()
      // Modify SCENARIO_MODIFIED activity for first version of each scenario to SCENARIO_CREATED
      _ <- updateActivityOfFirstVersionModificationToScenarioCreated()
      // Insert INCOMING_MIGRATION activities based on legacy comments
      _ <- insertModifiedIncomingMigrationActivities()
      // Delete activities with legacy comment corresponding to INCOMING_MIGRATION
      _ <- deleteOldIncomingMigrationActivities()
      // Insert AUTOMATIC_UPDATE activities based on legacy comments
      _ <- insertModifiedAutomaticUpdateActivities()
      // Delete activities with legacy comment corresponding to AUTOMATIC_UPDATE
      _ <- deleteOldAutomaticUpdateActivities()
      // Insert SCENARIO_NAME_CHANGED activities based on legacy comments
      _ <- insertModifiedScenarioRenameActivities()
      // Delete activities with legacy comment corresponding to SCENARIO_NAME_CHANGED
      _ <- deleteOldScenarioRenameActivities()
    } yield ()

    private def insertScenarioModifiedActivityForEachVersionWithoutActivity(): DBIOAction[Int, NoStream, Effect.All] = {
      val insertQuery =
        processVersionsDefinitions.table
          .joinLeft(scenarioActivitiesDefinitions.scenarioActivitiesTable)
          .on { (processVersionEntity, scenarioActivityEntity) =>
            processVersionEntity.processId === scenarioActivityEntity.scenarioId &&
            processVersionEntity.id === scenarioActivityEntity.scenarioVersion &&
            scenarioActivityEntity.activityType === "SCENARIO_MODIFIED" &&
            processVersionEntity.id > 1L
          }
          .filter { case (_, activity) => activity.isEmpty }
          .map(_._1)
          .map { version =>
            (
              ScenarioActivityType.ScenarioModified.entryName,         // activityType - converted from action name
              version.processId,                                       // scenarioId
              new JdbcFunction("generate_random_uuid").column[UUID](), // activityId
              None: Option[String],                                    // userId - always absent in old actions
              version.user,                                            // userName
              None: Option[String],                                    // impersonatedByUserId
              None: Option[String],                                    // impersonatedByUserName
              version.user.?,                                          // lastModifiedByUserName
              version.createDate.?,                                    // lastModifiedAt
              version.createDate,                                      // createdAt
              version.id.?,                                            // scenarioVersion
              None: Option[String],                                    // comment
              None: Option[Long],                                      // attachmentId
              version.createDate.?,                                    // finishedAt
              None: Option[String],                                    // state
              None: Option[String],                                    // errorMessage
              None: Option[String],                                    // buildInfo - always absent in old actions
              "{}"                                                     // additionalProperties
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    private def updateActivityOfFirstVersionModificationToScenarioCreated(): DBIOAction[Int, NoStream, Effect.All] = {
      scenarioActivitiesDefinitions.scenarioActivitiesTable
        .filter { scenarioActivityEntity =>
          scenarioActivityEntity.activityType === "SCENARIO_MODIFIED" &&
          scenarioActivityEntity.scenarioVersion === 1L
        }
        .map(_.activityType)
        .update(ScenarioActivityType.ScenarioCreated.entryName)
    }

    private def insertModifiedIncomingMigrationActivities(): DBIOAction[Int, NoStream, Effect.All] = {
      val insertQuery =
        scenarioActivitiesDefinitions.scenarioActivitiesTable
          .filter(_.comment.like("Scenario migrated from % by %"))
          .map { entity =>
            (
              ScenarioActivityType.IncomingMigration.entryName,        // activityType - converted from action name
              entity.scenarioId,                                       // scenarioId
              new JdbcFunction("generate_random_uuid").column[UUID](), // activityId
              entity.userId,                                           // userId
              entity.userName,                                         // userName
              entity.impersonatedByUserName,                           // impersonatedByUserId
              entity.impersonatedByUserName,                           // impersonatedByUserName
              entity.lastModifiedByUserName,                           // lastModifiedByUserName
              entity.lastModifiedAt,                                   // lastModifiedAt
              entity.createdAt,                                        // createdAt
              entity.scenarioVersion,                                  // scenarioVersion
              None: Option[String],                                    // comment
              entity.attachmentId,                                     // attachmentId
              entity.performedAt,                                      // finishedAt
              entity.state,                                            // state
              entity.errorMessage,                                     // errorMessage
              entity.buildInfo,                                        // buildInfo - always absent in old actions
              incomingMigrationPropertiesFromComment(entity.comment)   // additionalProperties
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    private def incomingMigrationPropertiesFromComment(comment: Rep[Option[String]]): Rep[String] = {
      comment
        .replace("Scenario migrated from ", """{"sourceEnvironment": """")
        .replace(" by ", """", "sourceUser": """")
        .++(""""}""")
        .getOrElse("{}")
    }

    private def deleteOldIncomingMigrationActivities(): DBIOAction[Int, NoStream, Effect.All] = {
      scenarioActivitiesDefinitions.scenarioActivitiesTable
        .filter(_.comment.like("Scenario migrated from % by %"))
        .delete
    }

    private def insertModifiedAutomaticUpdateActivities(): DBIOAction[Int, NoStream, Effect.All] = {
      val insertQuery =
        scenarioActivitiesDefinitions.scenarioActivitiesTable
          .filter { scenarioActivityEntity =>
            scenarioActivityEntity.comment.like("Migrations applied: %")
          }
          .map { entity =>
            (
              ScenarioActivityType.AutomaticUpdate.entryName,          // activityType - converted from action name
              entity.scenarioId,                                       // scenarioId
              new JdbcFunction("generate_random_uuid").column[UUID](), // activityId
              entity.userId,                                           // userId
              entity.userName,                                         // userName
              entity.impersonatedByUserName,                           // impersonatedByUserId
              entity.impersonatedByUserName,                           // impersonatedByUserName
              entity.lastModifiedByUserName,                           // lastModifiedByUserName
              entity.lastModifiedAt,                                   // lastModifiedAt
              entity.createdAt,                                        // createdAt
              entity.scenarioVersion,                                  // scenarioVersion
              None: Option[String],                                    // comment
              entity.attachmentId,                                     // attachmentId
              entity.performedAt,                                      // finishedAt
              entity.state,                                            // state
              entity.errorMessage,                                     // errorMessage
              entity.buildInfo,                                        // buildInfo - always absent in old actions
              automaticUpdatePropertiesFromComment(entity.comment)     // additionalProperties
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    private def automaticUpdatePropertiesFromComment(comment: Rep[Option[String]]): Rep[String] = {
      comment
        .replace("Migrations applied: ", """{"description": """")
        .++(""""}""")
        .getOrElse("{}")
    }

    private def deleteOldAutomaticUpdateActivities(): DBIOAction[Int, NoStream, Effect.All] = {
      scenarioActivitiesDefinitions.scenarioActivitiesTable
        .filter(_.comment.like("Migrations applied: %"))
        .delete
    }

    private def insertModifiedScenarioRenameActivities(): DBIOAction[Int, NoStream, Effect.All] = {
      val insertQuery =
        scenarioActivitiesDefinitions.scenarioActivitiesTable
          .filter { scenarioActivityEntity =>
            scenarioActivityEntity.comment.like("Rename: [%] -> [%]")
          }
          .map { entity =>
            (
              ScenarioActivityType.ScenarioNameChanged.entryName,      // activityType - converted from action name
              entity.scenarioId,                                       // scenarioId
              new JdbcFunction("generate_random_uuid").column[UUID](), // activityId
              entity.userId,                                           // userId
              entity.userName,                                         // userName
              entity.impersonatedByUserName,                           // impersonatedByUserId
              entity.impersonatedByUserName,                           // impersonatedByUserName
              entity.lastModifiedByUserName,                           // lastModifiedByUserName
              entity.lastModifiedAt,                                   // lastModifiedAt
              entity.createdAt,                                        // createdAt
              entity.scenarioVersion,                                  // scenarioVersion
              None: Option[String],                                    // comment
              entity.attachmentId,                                     // attachmentId
              entity.performedAt,                                      // finishedAt
              entity.state,                                            // state
              entity.errorMessage,                                     // errorMessage
              entity.buildInfo,                                        // buildInfo - always absent in old actions
              scenarioRenamePropertiesFromComment(entity.comment)      // additionalProperties
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    private def scenarioRenamePropertiesFromComment(comment: Rep[Option[String]]): Rep[String] = {
      comment.reverseString
        .drop(1)
        .reverseString
        .replace("Rename: [", """{"oldName": """")
        .replace("] -> [", """", "newName": """")
        .++(""""}""")
        .getOrElse("{}")
    }

    private def deleteOldScenarioRenameActivities(): DBIOAction[Int, NoStream, Effect.All] = {
      scenarioActivitiesDefinitions.scenarioActivitiesTable
        .filter(_.comment.like("Rename: [%] -> [%]"))
        .delete
    }

  }

  class ProcessVersionsDefinitions(val profile: JdbcProfile) {

    import profile.api._

    val table: TableQuery[ProcessVersionEntity] =
      LTableQuery(new ProcessVersionEntity(_))

    class ProcessVersionEntity(tag: Tag) extends Table[ProcessVersionEntityData](tag, "process_versions") {

      def id: Rep[Long] = column[Long]("id", NotNull)

      def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

      def user: Rep[String] = column[String]("user", NotNull)

      def processId: Rep[Long] = column[Long]("process_id", NotNull)

      override def * =
        (id, processId, createDate, user) <> (ProcessVersionEntityData.apply _ tupled, ProcessVersionEntityData.unapply)
    }

  }

  final case class ProcessVersionEntityData(
      id: Long,
      processId: Long,
      createDate: Timestamp,
      user: String,
  )

}
