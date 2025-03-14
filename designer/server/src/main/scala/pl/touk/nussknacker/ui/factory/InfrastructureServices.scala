package pl.touk.nussknacker.ui.factory

import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner

import java.time.Clock

final case class InfrastructureServices(
    clock: Clock,
    dbRef: DbRef,
    dbioRunner: DBIOActionRunner,
    metricsRegistry: MetricRegistry
)
