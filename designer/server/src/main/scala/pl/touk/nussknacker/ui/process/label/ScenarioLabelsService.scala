package pl.touk.nussknacker.ui.process.label

import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioLabelsRepository}

import scala.concurrent.{ExecutionContext, Future}

class ScenarioLabelsService(scenarioLabelsRepository: ScenarioLabelsRepository, dbioRunner: DBIOActionRunner)(
    implicit ec: ExecutionContext
) {

  def readLabels(): Future[List[String]] = {
    dbioRunner
      .run(scenarioLabelsRepository.getLabels)
      .map {
        _.toList.flatMap(_._2).map(_.value).distinct.sorted
      }
  }

}
