package pl.touk.nussknacker.engine.flink.api

import java.util

import scala.collection.JavaConverters._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters
import pl.touk.nussknacker.engine.api.ProcessVersion

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
//Also, those configuration properties will be exposed via Flienk REST API/webconsole
case class NkGlobalParameters(buildInfo: String,
                              processVersion: ProcessVersion,
                              useLegacyMetrics: Option[Boolean]) extends GlobalJobParameters {

  //here we decide which configuration properties should be shown in REST API etc.
  //For now it will be only deployment information
  override def toMap: util.Map[String, String] = {
    Map[String, Any](
      "buildInfo" -> buildInfo,
      "versionId" -> processVersion.versionId,
      "modelVersion" -> processVersion.modelVersion,
      "user" -> processVersion.user
    ).mapValues {
      case None => "none"
      case Some(x) => x.toString
      case null => "null"
      case x => x.toString
    }.asJava
  }
}

object NkGlobalParameters {

  def readFromContext(ec: ExecutionConfig): Option[NkGlobalParameters] = Option(ec.getGlobalJobParameters).collect {
    case a:NkGlobalParameters => a
  }

}
