package pl.touk.esp.ui

import com.typesafe.config.Config
import pl.touk.esp.engine.api.deployment.ProcessManager
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition.{ConfigCreatorTestInfoProvider, ProcessDefinitionProvider}
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.engine.standalone.management.StandaloneProcessManager
import pl.touk.esp.ui.app.BuildInfo
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType

case class ProcessingTypeDeps(processDefinitions: Map[ProcessingType, ProcessDefinition[ObjectDefinition]],
                              validators: Map[ProcessingType, ProcessValidator],
                              managers: Map[ProcessingType, ProcessManager with ConfigCreatorTestInfoProvider with ProcessDefinitionProvider],
                              buildInfo: Map[String, String],
                              standaloneModeEnabled: Boolean
                             )

object ProcessingTypeDeps {
  def apply(config: Config): ProcessingTypeDeps = {
    val streaming = ProcessingType.Streaming
    val reqResp = ProcessingType.RequestResponse
    val streamingManager = FlinkProcessManager(config)
    val processDefinition = streamingManager.getProcessDefinition
    val streamingBuildInfo = BuildInfo.ordered(streamingManager.buildInfo)
    val standaloneModeEnabled = config.hasPath("standaloneModeEnabled") && config.getBoolean("standaloneModeEnabled")
    val validator = ProcessValidator.default(processDefinition)
    if (standaloneModeEnabled) {
      val requestResponseManager = new StandaloneProcessManager(config)
      val requestResponseProcessDefinition = requestResponseManager.getProcessDefinition
      val requestResponseValidator = ProcessValidator.default(requestResponseProcessDefinition)

      ProcessingTypeDeps(
        processDefinitions = Map(streaming -> processDefinition, reqResp -> requestResponseProcessDefinition),
        validators = Map(streaming -> validator, reqResp -> requestResponseValidator),
        managers = Map(streaming -> streamingManager, reqResp -> requestResponseManager),
        buildInfo = streamingBuildInfo,
        standaloneModeEnabled = standaloneModeEnabled
      )
    } else {
      ProcessingTypeDeps(
        processDefinitions = Map(streaming -> processDefinition),
        validators = Map(streaming -> validator),
        managers = Map(streaming -> streamingManager),
        buildInfo = streamingBuildInfo,
        standaloneModeEnabled = standaloneModeEnabled
      )
    }
  }
}