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
                              configParameters: Option[ConfigGlobalParameters]) extends GlobalJobParameters {

  //here we decide which configuration properties should be shown in REST API etc.
  //For now it will be only deployment information
  //NOTE: this information is used in FlinkRestManager - any changes here should be reflected there
  override def toMap: util.Map[String, String] = {
    Map[String, String](
      "buildInfo" -> buildInfo,
      "versionId" -> processVersion.versionId.toString,
      "modelVersion" -> processVersion.modelVersion.map(_.toString).orNull,
      "user" -> processVersion.user
    ).filterNot(_._2 == null).asJava
  }
}

//this is part of global parameters that is parsed with typesafe Config (e.g. from application.conf/model.conf)
case class ConfigGlobalParameters(useLegacyMetrics: Option[Boolean])

object NkGlobalParameters {

  def readFromContext(ec: ExecutionConfig): Option[NkGlobalParameters] = Option(ec.getGlobalJobParameters).collect {
    case a:NkGlobalParameters => a
  }

}
