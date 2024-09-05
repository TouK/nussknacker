package pl.touk.nussknacker.ui.process.label

import cats.data.ValidatedNel
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioLabelsRepository}
import pl.touk.nussknacker.ui.validation.ScenarioLabelsValidator

import scala.concurrent.{ExecutionContext, Future}

class ScenarioLabelsService(
    scenarioLabelsRepository: ScenarioLabelsRepository,
    scenarioLabelsValidator: ScenarioLabelsValidator,
    dbioRunner: DBIOActionRunner
)(
    implicit ec: ExecutionContext
) {

  def readLabels(): Future[List[String]] = {
    dbioRunner
      .run(scenarioLabelsRepository.getLabels)
      .map {
        _.toList.flatMap(_._2).map(_.value).distinct.sorted
      }
  }

  def validatedScenarioLabels(labels: List[String]): ValidatedNel[ScenarioLabelsValidator.ValidationError, Unit] =
    scenarioLabelsValidator.validate(labels.map(ScenarioLabel.apply))

}
