package pl.touk.nussknacker.defaultmodel

import pl.touk.nussknacker.defaultmodel.migrations._
import pl.touk.nussknacker.engine.migration.{ProcessMigration, ProcessMigrations}

class DefaultModelMigrations extends ProcessMigrations {

  override def processMigrations: Map[Int, ProcessMigration] = Map(
    1 -> GroupByMigration,
    2 -> SinkExpressionMigration,
    3 -> RequestResponseSinkValidationModeMigration,
    4 -> DecisionTableParameterNamesMigration,
    5 -> PeriodicToSampleGeneratorMigration,
    // 100 -> NewMigration,
    // Newly added migrations should be in the hundreds: 100, 200, 300 and so on. We do this because
    // many ProcessMigrations can be loaded using SPI, and we want to avoid overlapping numbers when merging.
    100 -> SampleGeneratorToEventGeneratorAndPeriodToScheduleParameter
  )

}
