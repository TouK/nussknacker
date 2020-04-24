package pl.touk.nussknacker.engine.flink.api

import java.util
import scala.collection.JavaConverters._

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
//Also, those configuration properties will be exposed via Flienk REST API/webconsole
//TODO: 
case class NKGlobalParameters(buildInfo: String,
                              versionId: Long,
                              modelVersion: Option[Int],
                              user: String,
                              useLegacyMetrics: Option[Boolean]) extends GlobalJobParameters {

  override def toMap: util.Map[String, String] = {
    getClass.getDeclaredFields.map(_.getName).zip(productIterator.toList).toMap.mapValues {
      case None => "none"
      case Some(x) => x.toString
      case null => "null"
      case x => x.toString
    }.asJava
  }
}

object NKGlobalParameters {

  def readFromContext(ec: ExecutionConfig): Option[NKGlobalParameters] = Option(ec.getGlobalJobParameters).collect {
    case a:NKGlobalParameters => a
  }

}
