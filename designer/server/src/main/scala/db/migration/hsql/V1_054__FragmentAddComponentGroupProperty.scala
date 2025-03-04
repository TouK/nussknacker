package db.migration.hsql

import db.migration.V1_054__FragmentAddComponentGroupPropertyDefinition
import slick.jdbc.HsqldbProfile

class V1_054__FragmentAddComponentGroupProperty extends V1_054__FragmentAddComponentGroupPropertyDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(HsqldbProfile)
}
