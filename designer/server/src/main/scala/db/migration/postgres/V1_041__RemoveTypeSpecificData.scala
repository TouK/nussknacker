package db.migration.postgres

import db.migration.V1_041__RemoveTypeSpecificDataDefinition
import slick.jdbc.PostgresProfile

class V1_041__RemoveTypeSpecificData extends V1_041__RemoveTypeSpecificDataDefinition {
  override protected lazy val profile = createProfileWithSchema(PostgresProfile)
}
