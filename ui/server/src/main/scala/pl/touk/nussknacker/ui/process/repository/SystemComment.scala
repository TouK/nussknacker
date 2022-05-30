package pl.touk.nussknacker.ui.process.repository

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.migration.ProcessMigration
import pl.touk.nussknacker.ui.listener.Comment

sealed trait SystemComment extends Comment

case class UpdateProcessComment(override val value: String) extends SystemComment

object UpdateProcessComment {
  implicit val encoder: Encoder[UpdateProcessComment] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[UpdateProcessComment] = Decoder.decodeString.map(UpdateProcessComment(_))
}

case class MigrationComment(migrationsApplied: List[ProcessMigration]) extends SystemComment {

  def message = (s"Migrations applied: ${migrationsApplied.map(_.description).mkString(", ")}")

  override def value: String = message
}

