package db.migration.hsql

import db.migration.V1_054__FragmentAddIsDeprecatedPropertyDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_054__FragmentAddIsDeprecatedProperty extends V1_054__FragmentAddIsDeprecatedPropertyDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
