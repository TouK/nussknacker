package db.migration.postgres

import db.migration.V1_067__CreateJvmTimezoneTableDefinition
import slick.jdbc.PostgresProfile

class V1_067__CreateJvmTimezoneTable extends V1_067__CreateJvmTimezoneTableDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(PostgresProfile)
}
