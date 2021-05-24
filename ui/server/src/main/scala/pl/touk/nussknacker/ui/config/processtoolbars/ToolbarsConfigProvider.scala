package pl.touk.nussknacker.ui.config.processtoolbars

import io.circe.generic.JsonCodec

@JsonCodec
case class ProcessToolbarsConfigWithId(id: String,
                                       topRight: Option[List[ProcessToolbars]],
                                       bottomRight: Option[List[ProcessToolbars]],
                                       topLeft: Option[List[ProcessToolbars]],
                                       bottomLeft: Option[List[ProcessToolbars]],
                                       hidden: Option[List[ProcessToolbars]])

object ProcessToolbarsConfigWithId {
  def apply(processId: String,
            category: String,
            isSubprocess: Boolean,
            topRight: Option[List[ProcessToolbars]],
            bottomRight: Option[List[ProcessToolbars]],
            topLeft: Option[List[ProcessToolbars]],
            bottomLeft: Option[List[ProcessToolbars]],
            hidden: Option[List[ProcessToolbars]]): ProcessToolbarsConfigWithId = {
    ProcessToolbarsConfigWithId(
      id = generateId(processId, category, isSubprocess),
      topRight = topRight,
      bottomRight = bottomRight,
      topLeft = topLeft,
      bottomLeft = bottomLeft,
      hidden = hidden
    )
  }

  private def generateId(processId: String,
                         category: String,
                         isSubprocess: Boolean): String =
    s"$processId-$category-$isSubprocess"
}

class ToolbarsConfigProvider(config: ProcessAndSubprocessToolbarsConfig) {

  def configForCategory(category: String, isSubprocess: Boolean = false): ProcessToolbarsConfig = {
    val currentConfig = if (isSubprocess) config.subprocessConfig else config.processConfig

    val defaultConfig = currentConfig.defaultConfig
    currentConfig.categoriesConfigs.get(category)
      .map(addDefaultToolbars(_, defaultConfig))
      .getOrElse(defaultConfig)
  }

  private def addDefaultToolbars(config: ProcessToolbarsConfig, defaultConfig: ProcessToolbarsConfig): ProcessToolbarsConfig = {
    val topRight = config.topRight.orElse(defaultConfig.topRight)
    val bottomRight = config.bottomRight.orElse(defaultConfig.bottomRight)
    val topLeft = config.topLeft.orElse(defaultConfig.topLeft)
    val bottomLeft = config.bottomLeft.orElse(defaultConfig.bottomLeft)
    val hidden = config.hidden.orElse(defaultConfig.hidden)

    ProcessToolbarsConfig(
      topRight = topRight,
      bottomRight = bottomRight,
      topLeft = topLeft,
      bottomLeft = bottomLeft,
      hidden = hidden
    )
  }
}
