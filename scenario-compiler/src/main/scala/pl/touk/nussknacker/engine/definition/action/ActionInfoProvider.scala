package pl.touk.nussknacker.engine.definition.action

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, StaticParameterConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait ActionInfoProvider {

  def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): ValidatedNel[
    ProcessCompilationError,
    Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, StaticParameterConfig]]]
  ]

}
