package pl.touk.nussknacker.ui.customhttpservice.services

import pl.touk.nussknacker.engine.api.db.DbRef

final class NussknackerServicesForCustomHttpService(
    val scenarioService: ScenarioService,
    val scenarioTestingService: ScenarioTestingService,
    val dbRef: DbRef
)
