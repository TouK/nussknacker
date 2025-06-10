package pl.touk.nussknacker.ui.customhttpservice.services

final class NussknackerServicesForCustomHttpService(
    val scenarioService: ScenarioService,
    val databaseRunner: DatabaseRunner,
    val dbRef: DbRefInstance
)
