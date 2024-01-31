package pl.touk.nussknacker.ui.config.scenariotoolbar

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

import java.util.UUID

object ScenarioToolbarsConfig {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  // It provides empty list when toolbar panel is not set
  implicit val panelListReader: ValueReader[List[ToolbarPanelConfig]] = new ValueReader[List[ToolbarPanelConfig]] {

    override def read(config: Config, path: String): List[ToolbarPanelConfig] = {
      config.getOrElse[List[Config]](path, List.empty).map(_.as[ToolbarPanelConfig])
    }

  }

}

final case class ScenarioToolbarsConfig(
    uuid: Option[UUID],
    topLeft: List[ToolbarPanelConfig],
    bottomLeft: List[ToolbarPanelConfig],
    topRight: List[ToolbarPanelConfig],
    bottomRight: List[ToolbarPanelConfig]
) {
  lazy val uuidCode: UUID = uuid.getOrElse(UUID.nameUUIDFromBytes(hashCode().toString.getBytes))
}
