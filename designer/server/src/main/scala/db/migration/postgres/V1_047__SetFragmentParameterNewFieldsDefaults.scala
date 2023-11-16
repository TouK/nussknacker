package db.migration.postgres

import db.migration.V1_047__SetFragmentParameterNewFieldsDefaultsDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_047__SetFragmentParameterNewFieldsDefaults extends V1_047__SetFragmentParameterNewFieldsDefaultsDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
