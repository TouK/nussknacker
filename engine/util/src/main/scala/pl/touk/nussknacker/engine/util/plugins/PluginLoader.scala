package pl.touk.nussknacker.engine.util.plugins

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import scala.reflect.ClassTag
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

class PluginLoader {

  def loadPlugins[T:ClassTag](config: Config, classLoader: ClassLoader): List[T] = {

    val pluginConfig = config.getAs[Map[String, Config]]("plugins").getOrElse(Map())

    ScalaServiceLoader
      .load[PluginCreator](classLoader)
      .filter(_.pluginInterface == implicitly[ClassTag[T]].runtimeClass)
      .flatMap(creator => pluginConfig.get(creator.name).map(creator.create).map(_.asInstanceOf[T]))
  }

}
