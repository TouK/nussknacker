package pl.touk.nussknacker.engine.definition.activity

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait ActionInfoProvider {

  def getActionParameters(processVersion: ProcessVersion, scenario: CanonicalProcess): Map[String, Map[String, Map[String, ParameterConfig]]]

}
