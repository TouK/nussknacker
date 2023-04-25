package pl.touk.nussknacker.engine.api

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Try

@ConfiguredJsonCodec sealed trait TypeSpecificData {
  val isSubprocess = this match {
    case _: ScenarioSpecificData => false
    case _: FragmentSpecificData => true
  }
  val toProperties: Map[String, String]

  // TODO: extract this to somewhere else?
  protected def toStringMap(seq: Seq[Option[(String, Any)]]): Map[String, String] = {
    seq.flatten
      .map(p => (p._1, p._2.toString))
      .toMap
  }
}

object TypeSpecificData {
  def apply(properties: Map[String, String], propertiesType: String): TypeSpecificData = {
    propertiesType match {
      case "StreamMetaData" => StreamMetaData(properties)
      case "LiteStreamMetaData" => LiteStreamMetaData(properties)
      case "RequestResponseMetaData" => RequestResponseMetaData(properties)
      case "FragmentSpecificData" => FragmentSpecificData(properties)
      case _ => throw new IllegalStateException(s"Unrecognized scenario metadata type: ${propertiesType}")
    }
  }
}

case class FragmentSpecificData(docsUrl: Option[String] = None) extends TypeSpecificData {
  override val toProperties: Map[String, String] = toStringMap(List(
    docsUrl.map("docsUrl" -> _)
  ))
}

object FragmentSpecificData {
  def apply(properties: Map[String, String]): FragmentSpecificData = {
    FragmentSpecificData(
      docsUrl = properties.get("docsUrl")
    )
  }
}

sealed trait ScenarioSpecificData extends TypeSpecificData

// TODO: rename to FlinkStreamMetaData
case class StreamMetaData(parallelism: Option[Int] = None,
                          //we assume it's safer to spill state to disk and fix performance than to fix heap problems...
                          spillStateToDisk: Option[Boolean] = Some(true),
                          useAsyncInterpretation: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends ScenarioSpecificData {

  def checkpointIntervalDuration: Option[Duration] = checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))

  override val toProperties: Map[String, String] = toStringMap(List(
    parallelism.map("parallelism" -> _),
    spillStateToDisk.map("spillStateToDisk" -> _),
    useAsyncInterpretation.map("useAsyncInterpretation" -> _),
    checkpointIntervalInSeconds.map("checkpointIntervalInSeconds" -> _)
  ))
}

object StreamMetaData {
  def apply(properties: Map[String, String]): StreamMetaData = {
    StreamMetaData(
      parallelism = properties.get("parallelism").flatMap(p => Try(p.toInt).toOption),
      spillStateToDisk = properties.get("spillStateToDisk").flatMap(p => Try(p.toBoolean).toOption),
      useAsyncInterpretation = properties.get("useAsyncInterpretation").flatMap(p => Try(p.toBoolean).toOption),
      checkpointIntervalInSeconds = properties.get("checkpointIntervalInSeconds").flatMap(p => Try(p.toLong).toOption)
    )
  }
}

// TODO: parallelism is fine? Maybe we should have other method to adjust number of workers?
case class LiteStreamMetaData(parallelism: Option[Int] = None) extends ScenarioSpecificData {
  override val toProperties: Map[String, String] = toStringMap(List(
    parallelism.map("parallelism" -> _)))
}

object LiteStreamMetaData {
  def apply(properties: Map[String, String]): LiteStreamMetaData = {
    LiteStreamMetaData(
      parallelism = properties.get("parallelism").flatMap(p => Try(p.toInt).toOption))
  }
}

case class RequestResponseMetaData(slug: Option[String]) extends ScenarioSpecificData {
  override val toProperties: Map[String, String] = toStringMap(List(
    slug.map("slug" -> _)))
}

object RequestResponseMetaData {
  def apply(properties: Map[String, String]): RequestResponseMetaData = {
    RequestResponseMetaData(
      slug = properties.get("slug"))
  }
}
