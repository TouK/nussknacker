package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.defaultmodel.migrations.{GroupByMigration, SinkExpressionMigration}
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}

class DefaultModelMigrations extends ProcessMigrations {

  override def processMigrations: Map[Int, ProcessMigration] = ProcessMigrations.listOf(GroupByMigration, SinkExpressionMigration).processMigrations
}
