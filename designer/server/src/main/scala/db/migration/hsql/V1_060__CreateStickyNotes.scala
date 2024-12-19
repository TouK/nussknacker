package db.migration.hsql

import db.migration.V1_060__CreateStickyNotesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_060__CreateStickyNotes extends V1_060__CreateStickyNotesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
