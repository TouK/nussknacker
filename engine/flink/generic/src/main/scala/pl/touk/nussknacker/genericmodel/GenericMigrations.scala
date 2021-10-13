package pl.touk.nussknacker.genericmodel

import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}
import pl.touk.nussknacker.genericmodel.migrations.{GroupByMigration, SinkExpressionMigration}

class GenericMigrations extends ProcessMigrations {

  override def processMigrations: Map[Int, ProcessMigration] = ProcessMigrations.listOf(GroupByMigration, SinkExpressionMigration).processMigrations
}
