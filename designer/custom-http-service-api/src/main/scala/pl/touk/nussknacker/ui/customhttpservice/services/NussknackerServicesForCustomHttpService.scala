package pl.touk.nussknacker.ui.customhttpservice.services

import scala.language.higherKinds

final class NussknackerServicesForCustomHttpService[M[_]](
    val scenarioService: ScenarioService[M],
)
