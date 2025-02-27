package pl.touk.nussknacker.engine.definition.action

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, ParameterConfig}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait ActionInfoProvider {

  def getActionParameters(
      processVersion: ProcessVersion,
      scenario: CanonicalProcess
  ): Map[ScenarioActionName, Map[NodeComponentInfo, Map[ParameterName, ParameterConfig]]]

}
