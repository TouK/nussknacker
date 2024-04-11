package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.util.{ApiAdapter, ApiAdapterService}

class MigrationApiAdapterService extends ApiAdapterService[MigrateScenarioData] {
  override def getAdapters: Map[Int, ApiAdapter[MigrateScenarioData]] = MigrationApiAdapters.adapters
}
