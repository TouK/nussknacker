package db.migration.hsql

import db.migration.{V1_013__GroupNodesChange => V1_013__GroupNodesChangeDefinition}
import slick.jdbc.HsqldbProfile

class V1_013__GroupNodesChange extends V1_013__GroupNodesChangeDefinition {

  override protected lazy val profile = createNuJdbcProfileFrom(HsqldbProfile)

}
