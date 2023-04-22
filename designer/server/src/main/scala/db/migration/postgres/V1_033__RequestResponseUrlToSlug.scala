package db.migration.postgres

import db.migration.{V1_033__RequestResponseUrlToSlug => V1_033__RequestResponseUrlToSlugDefinition}
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_033__RequestResponseUrlToSlug extends V1_033__RequestResponseUrlToSlugDefinition{
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
