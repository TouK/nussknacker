package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_055__CreateScenarioActivitiesDefinition.ScenarioActivitiesDefinitions
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

trait V1_055__CreateScenarioActivitiesDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  private val definitions = new ScenarioActivitiesDefinitions(profile)

  override def migrateActions: DBIOAction[Any, NoStream, _ <: Effect] = {
    logger.info("Starting migration V1_055__CreateScenarioActivitiesDefinition")
    definitions.scenarioActivitiesTable.schema.create
      .map(_ => logger.info("Execution finished for migration V1_055__CreateScenarioActivitiesDefinition"))
  }

}

object V1_055__CreateScenarioActivitiesDefinition {

  class ScenarioActivitiesDefinitions(val profile: JdbcProfile) {
    import profile.api._

    val scenarioActivitiesTable = TableQuery[ScenarioActivityEntity]

    class ScenarioActivityEntity(tag: Tag) extends Table[ScenarioActivityEntityData](tag, "scenario_activities") {

      def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

      def activityType: Rep[String] = column[String]("activity_type", NotNull)

      def scenarioId: Rep[Long] = column[Long]("scenario_id", NotNull)

      def activityId: Rep[UUID] = column[UUID]("activity_id", NotNull)

      def userId: Rep[Option[String]] = column[Option[String]]("user_id")

      def userName: Rep[String] = column[String]("user_name", NotNull)

      def impersonatedByUserId: Rep[Option[String]] = column[Option[String]]("impersonated_by_user_id")

      def impersonatedByUserName: Rep[Option[String]] = column[Option[String]]("impersonated_by_user_name")

      def lastModifiedByUserName: Rep[Option[String]] = column[Option[String]]("last_modified_by_user_name")

      def lastModifiedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("last_modified_at")

      def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

      def scenarioVersion: Rep[Option[Long]] = column[Option[Long]]("scenario_version")

      def comment: Rep[Option[String]] = column[Option[String]]("comment")

      def attachmentId: Rep[Option[Long]] = column[Option[Long]]("attachment_id")

      def performedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("performed_at")

      def state: Rep[Option[String]] = column[Option[String]]("state")

      def errorMessage: Rep[Option[String]] = column[Option[String]]("error_message")

      def buildInfo: Rep[Option[String]] = column[Option[String]]("build_info")

      def additionalProperties: Rep[String] = column[String]("additional_properties", NotNull)

      def tuple: (
          Rep[String],
          Rep[Long],
          Rep[UUID],
          Rep[Option[String]],
          Rep[String],
          Rep[Option[String]],
          Rep[Option[String]],
          Rep[Option[String]],
          Rep[Option[Timestamp]],
          Rep[Timestamp],
          Rep[Option[Long]],
          Rep[Option[String]],
          Rep[Option[Long]],
          Rep[Option[Timestamp]],
          Rep[Option[String]],
          Rep[Option[String]],
          Rep[Option[String]],
          Rep[String]
      ) = (
        activityType,
        scenarioId,
        activityId,
        userId,
        userName,
        impersonatedByUserId,
        impersonatedByUserName,
        lastModifiedByUserName,
        lastModifiedAt,
        createdAt,
        scenarioVersion,
        comment,
        attachmentId,
        performedAt,
        state,
        errorMessage,
        buildInfo,
        additionalProperties,
      )

      override def * =
        (
          id,
          activityType,
          scenarioId,
          activityId,
          userId,
          userName,
          impersonatedByUserId,
          impersonatedByUserName,
          lastModifiedByUserName,
          lastModifiedAt,
          createdAt,
          scenarioVersion,
          comment,
          attachmentId,
          performedAt,
          state,
          errorMessage,
          buildInfo,
          additionalProperties,
        ) <> (
          ScenarioActivityEntityData.apply _ tupled, ScenarioActivityEntityData.unapply
        )

    }

  }

  final case class ScenarioActivityEntityData(
      id: Long,
      activityType: String,
      scenarioId: Long,
      activityId: UUID,
      userId: Option[String],
      userName: String,
      impersonatedByUserId: Option[String],
      impersonatedByUserName: Option[String],
      lastModifiedByUserName: Option[String],
      lastModifiedAt: Option[Timestamp],
      createdAt: Timestamp,
      scenarioVersion: Option[Long],
      comment: Option[String],
      attachmentId: Option[Long],
      finishedAt: Option[Timestamp],
      state: Option[String],
      errorMessage: Option[String],
      buildInfo: Option[String],
      additionalProperties: String,
  )

}
