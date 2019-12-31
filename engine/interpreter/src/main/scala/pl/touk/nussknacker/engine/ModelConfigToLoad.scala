package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

object ModelConfigToLoad {

  val modelConfigResource = "model.conf"

}

/* Config of model can come from following locations:
   - part of main config file of nussknacker (in path processTypes.{type_name}.modelConfig
   - model.conf from model jar
   - reference.conf from model jar
   This class stores configs only from first location (i.e. from main config file), as it's only part that has to be e.g.
   passed to Flink. We separate it from the rest of config to keep it small (see e.g. FlinkProcessCompiler)
 */
case class ModelConfigToLoad(config: Config) {

  def loadConfig(classLoader: ClassLoader): Config = {
    /*
      We want to be able to embed config in model jar, to avoid excessive config files
      For most cases using reference.conf would work, however there are subtle problems with substitution:
      https://github.com/lightbend/config#note-about-resolving-substitutions-in-referenceconf-and-applicationconf
      https://github.com/lightbend/config/issues/167
      By using separate model.conf we can define configs there like:
      service1Url: ${baseUrl}/service1
      and have baseUrl taken from application config
     */
    val configFallbackFromModel = ConfigFactory.parseResources(classLoader, modelConfigResource)
    config
      .withFallback(configFallbackFromModel)
      //this is for reference.conf resources from model jar
      .withFallback(ConfigFactory.load(classLoader))
      .resolve()
  }

  def render(): String = config.root().render(ConfigRenderOptions.concise())

  //only for testing
  private[engine] def modelConfigResource: String = ModelConfigToLoad.modelConfigResource

}
