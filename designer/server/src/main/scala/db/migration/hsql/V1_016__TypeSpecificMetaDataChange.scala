package db.migration.hsql

import db.migration.{V1_016__TypeSpecificMetaDataChange => V1_016__TypeSpecificMetaDataChangeDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_016__TypeSpecificMetaDataChange extends V1_016__TypeSpecificMetaDataChangeDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
