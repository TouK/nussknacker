package pl.touk.nussknacker.ui

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader

package object config {

  implicit object Implicits extends LazyLogging {

    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._

    def parseOptionalConfig[T](config: Config,path: String)(implicit reader: ValueReader[T]): Option[T] = {
      if(config.hasPath(path)) {
        logger.debug(s"Found optional config at path=$path, parsing...")
        Some(config.as[T](path))
      } else {
        logger.debug(s"Optional config at path=$path not found, skipping.")
        None
      }
    }
  }
}
