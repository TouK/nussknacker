package pl.touk.nussknacker.engine.flink.api

import java.util
import java.util.Collections

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
case class NKGlobalParameters(useLegacyMetrics: Option[Boolean]) extends GlobalJobParameters {
  override def toMap: util.Map[String, String] = Collections.singletonMap("useLegacyMetrics", useLegacyMetrics.map(_.toString).getOrElse("none"))
}

object NKGlobalParameters {

  def readFromContext(ec: ExecutionConfig): Option[NKGlobalParameters] = Option(ec.getGlobalJobParameters).collect {
    case a:NKGlobalParameters => a
  }

}
