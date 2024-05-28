package pl.touk.nussknacker.ui.process.repository

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.migration.ProcessMigration
import pl.touk.nussknacker.ui.listener.Comment

sealed trait SystemComment extends Comment

final case class UpdateProcessComment(override val value: String) extends SystemComment {
  require(value.nonEmpty, "Comment can't be empty")
}

object UpdateProcessComment {
  implicit val encoder: Encoder[UpdateProcessComment] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[Option[UpdateProcessComment]] =
    Decoder.decodeOption(Decoder.decodeString.map(UpdateProcessComment(_))).map(_.filterNot(_.value.isEmpty))
}

final case class MigrationComment(migrationsApplied: List[ProcessMigration]) extends SystemComment {

  override def value: String = s"Migrations applied: ${migrationsApplied.map(_.description).mkString(", ")}"
}
