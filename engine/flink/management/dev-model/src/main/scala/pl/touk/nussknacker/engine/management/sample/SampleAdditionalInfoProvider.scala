package pl.touk.nussknacker.engine.management.sample

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.additionalInfo.{MarkdownAdditionalInfo, AdditionalInfo, AdditionalInfoProvider}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.engine.graph.service.ServiceRef

import scala.concurrent.Future

//used in NodeResourcesSpec
class SampleAdditionalInfoProvider extends AdditionalInfoProvider {

  override def nodeAdditionalInfo(config: Config)(nodeData: node.NodeData): Future[Option[AdditionalInfo]] = {
    nodeData match {
      case Enricher(_, ServiceRef("paramService", idParam :: Nil), _, _) => Future.successful(Some {
        val id = idParam.expression.expression.replace("'", "")
        MarkdownAdditionalInfo(
          s"""
            |Samples:
            |
            || id  | value |
            || --- | ----- |
            || a   | generated |
            || b   | not existent |
            |
            |Results for $id can be found [here](http://touk.pl?id=$id)
            |""".stripMargin)
      })
      case _ => Future.successful(None)
    }
  }

  override def propertiesAdditionalInfo(config: Config)(metaData: MetaData): Future[Option[AdditionalInfo]] = {
    val properties = metaData.additionalFields.map(_.properties)
    (properties.flatMap(_.get("environment")), properties.flatMap(_.get("numberOfThreads"))) match {
      case (Some(environment), Some(numberOfThreads)) => Future.successful(Some {
        MarkdownAdditionalInfo(s"$numberOfThreads threads will be used on environment '$environment'")
      })
      case _ => Future.successful(None)
    }
  }
}
