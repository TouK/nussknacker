package pl.touk.nussknacker.engine.spel

import com.typesafe.config.Config

object SpelConfig {
  
  def enableSpelForceCompile(config: Config): Boolean = {
    val path = "enableSpelForceCompile"
    if (config.hasPath(path)) {
      config.getBoolean(path)
    } else {
      true
    }
  }
}
