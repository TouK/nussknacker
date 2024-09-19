package pl.touk.nussknacker.ui.process.label

import cats.data.ValidatedNel
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioLabelsRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.ScenarioLabelsValidator

import scala.concurrent.{ExecutionContext, Future}

class ScenarioLabelsService(
    scenarioLabelsRepository: ScenarioLabelsRepository,
    scenarioLabelsValidator: ScenarioLabelsValidator,
    dbioRunner: DBIOActionRunner
)(
    implicit ec: ExecutionContext
) {

  def readLabels(loggedUser: LoggedUser): Future[List[String]] = {
    dbioRunner
      .run(scenarioLabelsRepository.getLabels(loggedUser))
      .map {
        _.map(_.value).sorted
      }
  }

  def validatedScenarioLabels(labels: List[String]): ValidatedNel[ScenarioLabelsValidator.ValidationError, Unit] =
    scenarioLabelsValidator.validate(labels.map(ScenarioLabel.apply))

}
