package db.migration.postgres

import db.migration.V1_068__CreateJvmTimezoneTableDefinition
import slick.jdbc.PostgresProfile

class V1_068__CreateJvmTimezoneTable extends V1_068__CreateJvmTimezoneTableDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(PostgresProfile)
}
