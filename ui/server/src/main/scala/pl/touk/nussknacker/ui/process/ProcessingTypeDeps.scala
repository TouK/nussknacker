package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorTestInfoProvider, ProcessDefinitionProvider, SignalDispatcher}
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.engine.management.FlinkProcessManager
import pl.touk.nussknacker.engine.standalone.management.StandaloneProcessManager
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType

case class ProcessingTypeDeps(processDefinitions: Map[ProcessingType, ProcessDefinition[ObjectDefinition]],
                              validators: Map[ProcessingType, ProcessValidator],
                              managers: Map[ProcessingType, ProcessManager with ConfigCreatorTestInfoProvider with ProcessDefinitionProvider with SignalDispatcher],
                              espQueryableClient: () => EspQueryableClient,
                              buildInfo: Map[String, String],
                              standaloneModeEnabled: Boolean
                             )

object ProcessingTypeDeps {
  def apply(config: Config, standaloneModeEnabled: Boolean): ProcessingTypeDeps = {
    val streaming = ProcessingType.Streaming
    val reqResp = ProcessingType.RequestResponse
    val streamingManager = FlinkProcessManager(config)
    val processDefinition = streamingManager.getProcessDefinition
    val streamingBuildInfo = BuildInfo.ordered(streamingManager.buildInfo)
    val validator = ProcessValidator.default(processDefinition)
    if (standaloneModeEnabled) {
      val requestResponseManager = new StandaloneProcessManager(config)
      val requestResponseProcessDefinition = requestResponseManager.getProcessDefinition
      val requestResponseValidator = ProcessValidator.default(requestResponseProcessDefinition)

      ProcessingTypeDeps(
        processDefinitions = Map(streaming -> processDefinition, reqResp -> requestResponseProcessDefinition),
        validators = Map(streaming -> validator, reqResp -> requestResponseValidator),
        managers = Map(streaming -> streamingManager, reqResp -> requestResponseManager),
        espQueryableClient = () => streamingManager.queryableClient,
        buildInfo = streamingBuildInfo,
        standaloneModeEnabled = standaloneModeEnabled
      )
    } else {
      ProcessingTypeDeps(
        processDefinitions = Map(streaming -> processDefinition),
        validators = Map(streaming -> validator),
        managers = Map(streaming -> streamingManager),
        espQueryableClient = () => streamingManager.queryableClient,
        buildInfo = streamingBuildInfo,
        standaloneModeEnabled = standaloneModeEnabled
      )
    }
  }
}