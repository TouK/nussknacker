package pl.touk.nussknacker.engine.additionalInfo

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.MetaData

import scala.concurrent.Future

/**
  * Trait allowing models to prepare additional info for scenario's properties (e.g. links, sample data etc.)
  * Implementations have to be registered via ServiceLoader mechanism.
  *
  * additionalInfo method is invoked when properties changes, so it should be relatively fast.
  */
trait PropertiesAdditionalInfoProvider {

  def additionalInfo(config: Config)(metaData: MetaData): Future[Option[NodeAdditionalInfo]]

}
