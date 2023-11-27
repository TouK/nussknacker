package db.migration.hsql

import db.migration.V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_048__SetFragmentParameterNewFieldsDefaults_2
    extends V1_048__SetFragmentParameterNewFieldsDefaultsDefinition_2 {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
