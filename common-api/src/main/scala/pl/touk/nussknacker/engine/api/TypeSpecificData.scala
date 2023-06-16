package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.TypeSpecificDataConversionUtils.{convertPropertyOrNone, mapEmptyStringToNone, toStringWithEmptyDefault}
import io.circe.generic.extras.ConfiguredJsonCodec

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.util.Try

@ConfiguredJsonCodec sealed trait TypeSpecificData {
  val isSubprocess: Boolean = this match {
    case _: ScenarioSpecificData => false
    case _: FragmentSpecificData => true
  }

  def toMap: Map[String, String]

  def metaDataType: String = {
    this match {
      case _: StreamMetaData => "StreamMetaData"
      case _: LiteStreamMetaData => "LiteStreamMetaData"
      case _: RequestResponseMetaData => "RequestResponseMetaData"
      case _: FragmentSpecificData => "FragmentSpecificData"
    }
  }
}

sealed trait ScenarioSpecificData extends TypeSpecificData

case class FragmentSpecificData(docsUrl: Option[String] = None) extends TypeSpecificData {
  override def toMap: Map[String, String] = Map(FragmentSpecificData.docsUrlName -> docsUrl.getOrElse(""))
}

object FragmentSpecificData {
  private val docsUrlName = "docsUrl"

  def apply(properties: Map[String, String]): FragmentSpecificData = {
    FragmentSpecificData(docsUrl = mapEmptyStringToNone(properties.get(docsUrlName)))
  }
}

// TODO: rename to FlinkStreamMetaData
case class StreamMetaData(parallelism: Option[Int] = None,
                          //we assume it's safer to spill state to disk and fix performance than to fix heap problems...
                          spillStateToDisk: Option[Boolean] = Some(true),
                          useAsyncInterpretation: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends ScenarioSpecificData {

  def checkpointIntervalDuration  : Option[Duration]= checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))

  override def toMap: Map[String, String] = Map(
      StreamMetaData.parallelismName -> toStringWithEmptyDefault(parallelism),
      StreamMetaData.spillStateToDiskName -> toStringWithEmptyDefault(spillStateToDisk),
      StreamMetaData.useAsyncInterpretationName -> toStringWithEmptyDefault(useAsyncInterpretation),
      StreamMetaData.checkpointIntervalInSecondsName -> toStringWithEmptyDefault(checkpointIntervalInSeconds),
    )

}

object StreamMetaData {
  private val parallelismName = "parallelism"
  private val spillStateToDiskName = "spillStateToDisk"
  private val useAsyncInterpretationName = "useAsyncInterpretation"
  private val checkpointIntervalInSecondsName = "checkpointIntervalInSeconds"

  def apply(properties: Map[String, String]): StreamMetaData = {
    StreamMetaData(
      parallelism = properties.get(parallelismName).flatMap(convertPropertyOrNone(_, _.toInt)),
      spillStateToDisk = properties.get(spillStateToDiskName).flatMap(convertPropertyOrNone(_, _.toBoolean)),
      useAsyncInterpretation = properties.get(useAsyncInterpretationName).flatMap(convertPropertyOrNone(_, _.toBoolean)),
      checkpointIntervalInSeconds = properties.get(checkpointIntervalInSecondsName).flatMap(convertPropertyOrNone(_, _.toLong))
    )
  }
}

// TODO: parallelism is fine? Maybe we should have other method to adjust number of workers?
case class LiteStreamMetaData(parallelism: Option[Int] = None) extends ScenarioSpecificData {
  override def toMap: Map[String, String] = Map(LiteStreamMetaData.parallelismName -> toStringWithEmptyDefault(parallelism))
}

object LiteStreamMetaData {
  private val parallelismName = "parallelism"

  def apply(properties: Map[String, String]): LiteStreamMetaData = {
    LiteStreamMetaData(
      parallelism = properties.get(parallelismName).flatMap(convertPropertyOrNone(_, _.toInt))
    )
  }
}

case class RequestResponseMetaData(slug: Option[String]) extends ScenarioSpecificData {
  override def toMap: Map[String, String] = Map(RequestResponseMetaData.slugName -> slug.getOrElse(""))
}

object RequestResponseMetaData {
  private val slugName = "slug"

  def apply(properties: Map[String, String]): RequestResponseMetaData = {
    RequestResponseMetaData(slug = mapEmptyStringToNone(properties.get(slugName)))
  }
}


object TypeSpecificDataConversionUtils {

  def toStringWithEmptyDefault(option: Option[Any]): String = {
    option.fold("")(_.toString)
  }

  def mapEmptyStringToNone(option: Option[String]): Option[String] = option match {
    case Some(s) if s.isEmpty => None
    case other => other
  }

  def convertPropertyOrNone[T](value: String, converter: String => T): Option[T] = {
    Try(converter(value))
      .map(Some(_))
      .recover { case _: IllegalArgumentException => None }
      .get
  }
}
