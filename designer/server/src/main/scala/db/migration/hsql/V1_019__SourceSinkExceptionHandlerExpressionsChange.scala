package db.migration.hsql

import db.migration.{
  V1_019__SourceSinkExceptionHandlerExpressionsChange => V1_019__SourceSinkExceptionHandlerExpressionsChangeDefinition
}
import slick.jdbc.HsqldbProfile

class V1_019__SourceSinkExceptionHandlerExpressionsChange
    extends V1_019__SourceSinkExceptionHandlerExpressionsChangeDefinition {
  override protected lazy val profile = createProfileWithSchema(HsqldbProfile)
}
