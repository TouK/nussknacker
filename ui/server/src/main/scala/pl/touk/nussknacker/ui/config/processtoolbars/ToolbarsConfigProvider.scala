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
  def apply(processName: String,
            category: String,
            isSubprocess: Boolean,
            config: ProcessToolbarsConfig): ProcessToolbarsConfigWithId = {
    ProcessToolbarsConfigWithId(
      id = generateId(processName, category, isSubprocess),
      topRight = config.topRight,
      bottomRight = config.bottomRight,
      topLeft = config.topLeft,
      bottomLeft = config.bottomLeft,
      hidden = config.hidden
    )
  }

  private def generateId(processName: String,
                         category: String,
                         isSubprocess: Boolean): String =
    s"$processName-$category-$isSubprocess"
}

class ToolbarsConfigProvider(config: ProcessAndSubprocessToolbarsConfig) {

  def configForCategory(processName: String, category: String, isSubprocess: Boolean = false): ProcessToolbarsConfigWithId = {
    val currentConfig = if (isSubprocess) config.subprocessConfig else config.processConfig

    val defaultConfig = currentConfig.defaultConfig
    val mergedConfig = currentConfig.categoriesConfigs.get(category)
      .map(addDefaultToolbars(_, defaultConfig))
      .getOrElse(defaultConfig)

    ProcessToolbarsConfigWithId(processName, category, isSubprocess, mergedConfig)
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
