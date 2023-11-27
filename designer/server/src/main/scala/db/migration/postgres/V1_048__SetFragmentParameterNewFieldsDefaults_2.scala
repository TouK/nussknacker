package db.migration.postgres

import db.migration.V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_048__SetFragmentParameterNewFieldsDefaults_2
    extends V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2 {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
