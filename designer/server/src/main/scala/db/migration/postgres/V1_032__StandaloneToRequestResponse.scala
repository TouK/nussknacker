package db.migration.postgres

import db.migration.V1_032__StandaloneToRequestResponseDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_032__StandaloneToRequestResponse extends V1_032__StandaloneToRequestResponseDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
