package pl.touk.nussknacker.ui.migrations

import pl.touk.nussknacker.ui.util.{ApiAdapter, ApiAdapterService}

class MigrationApiAdapterService extends ApiAdapterService[MigrateScenarioRequest] {
  override def getAdapters: Map[Int, ApiAdapter[MigrateScenarioRequest]] = MigrationApiAdapters.adapters
}
