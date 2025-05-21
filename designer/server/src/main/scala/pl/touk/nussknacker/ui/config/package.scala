package pl.touk.nussknacker.ui

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.ValueReader

import scala.jdk.CollectionConverters._

package object config {

  implicit object Implicits extends LazyLogging {

    implicit class ConfigExt(config: Config) {

      // this method was created only to provide debug info, otherwise we'd use getAs[T]
      def getAsWithLogging[T](path: String)(implicit reader: ValueReader[T]): Option[T] = {
        if (config.hasPath(path)) {
          logger.debug(s"Found optional config at path=$path, parsing...")
          import net.ceedubs.ficus.Ficus._
          Some(config.as[T](path))
        } else {
          logger.debug(s"Optional config at path=$path not found, skipping.")
          None
        }
      }

      // Without this ConfigException$BadPath is thrown
      def rootAs[T: ValueReader]: T = ConfigFactory.parseMap(Map("root" -> config.root()).asJava).as[T]("root")

    }

  }

}
