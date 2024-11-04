package db.migration.postgres

import db.migration.V1_060__CreateStickyNotesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_060__CreateStickyNotes extends V1_060__CreateStickyNotesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
