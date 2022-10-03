package pl.touk.nussknacker.ui

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader

package object config {

  implicit object Implicits extends LazyLogging {

    // this method was created only to provide debug info, otherwise we'd use getAs[T]
    def parseOptionalConfig[T](config: Config, path: String)(implicit reader: ValueReader[T]): Option[T] = {
      if (config.hasPath(path)) {
        logger.debug(s"Found optional config at path=$path, parsing...")
        import net.ceedubs.ficus.Ficus._
        Some(config.as[T](path))
      } else {
        logger.debug(s"Optional config at path=$path not found, skipping.")
        None
      }
    }
  }
}
