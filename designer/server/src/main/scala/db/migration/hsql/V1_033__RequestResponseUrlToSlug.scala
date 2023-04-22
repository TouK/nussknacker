package db.migration.hsql

import db.migration.{V1_033__RequestResponseUrlToSlug => V1_033__RequestResponseUrlToSlugDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_033__RequestResponseUrlToSlug extends V1_033__RequestResponseUrlToSlugDefinition{
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
