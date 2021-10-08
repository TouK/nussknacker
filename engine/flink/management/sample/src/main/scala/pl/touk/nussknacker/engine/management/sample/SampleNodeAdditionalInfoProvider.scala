package pl.touk.nussknacker.engine.management.sample

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.additionalInfo.{MarkdownNodeAdditionalInfo, NodeAdditionalInfo, NodeAdditionalInfoProvider}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.engine.graph.service.ServiceRef

import scala.concurrent.Future

//used in NodeResourcesSpec
class SampleNodeAdditionalInfoProvider extends NodeAdditionalInfoProvider {

  override def additionalInfo(config: Config)(nodeData: node.NodeData): Future[Option[NodeAdditionalInfo]] = {
    nodeData match {
      case Enricher(_, ServiceRef("paramService", idParam :: Nil), _, _) => Future.successful(Some {
        val id = idParam.expression.expression.replace("'", "")
        MarkdownNodeAdditionalInfo(
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
}
