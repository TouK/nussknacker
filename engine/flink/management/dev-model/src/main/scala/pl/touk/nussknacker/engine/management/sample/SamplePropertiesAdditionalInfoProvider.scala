package pl.touk.nussknacker.engine.management.sample

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.additionalInfo.{MarkdownNodeAdditionalInfo, NodeAdditionalInfo, PropertiesAdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.MetaData

import scala.concurrent.Future

class SamplePropertiesAdditionalInfoProvider extends PropertiesAdditionalInfoProvider {

  override def additionalInfo(config: Config)(metaData: MetaData): Future[Option[NodeAdditionalInfo]] = {
    val properties = metaData.additionalFields.map(_.properties)
    (properties.flatMap(_.get("environment")), properties.flatMap(_.get("numberOfThreads"))) match {
      case (Some(environment), Some(numberOfThreads)) => Future.successful(Some {
        MarkdownNodeAdditionalInfo(s"$numberOfThreads threads will be used on environment '$environment'")
      })
      case _ => Future.successful(None)
    }
  }
}
