package pl.touk.nussknacker.ui.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.engine.management.{FlinkModelData, FlinkProcessManager}
import pl.touk.nussknacker.engine.standalone.StandaloneModelData
import pl.touk.nussknacker.engine.standalone.management.StandaloneProcessManager
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType

case class ProcessingTypeDeps(managers: Map[ProcessingType, ProcessManager],
                              espQueryableClient: () => EspQueryableClient,
                              modelData: Map[ProcessingType, ModelData])

object ProcessingTypeDeps {
  def apply(config: Config, standaloneModeEnabled: Boolean): ProcessingTypeDeps = {
    val streaming = ProcessingType.Streaming
    val reqResp = ProcessingType.RequestResponse
    val streamingData = FlinkModelData(config)
    val streamingManager = FlinkProcessManager(streamingData, config)

    if (standaloneModeEnabled) {
      val requestResponseData = StandaloneModelData(config)
      val requestResponseManager = StandaloneProcessManager(requestResponseData, config)

      ProcessingTypeDeps(
        managers = Map(streaming -> streamingManager, reqResp -> requestResponseManager),
        espQueryableClient = () => streamingManager.queryableClient,
        modelData = Map(streaming -> streamingData, reqResp -> requestResponseData)
      )
    } else {
      ProcessingTypeDeps(
        managers = Map(streaming -> streamingManager),
        espQueryableClient = () => streamingManager.queryableClient,
        modelData = Map(streaming -> streamingData)
      )
    }
  }
}