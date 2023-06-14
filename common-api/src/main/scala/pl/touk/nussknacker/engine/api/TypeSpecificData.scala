package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.TypeSpecificDataConversionUtils.{convertPropertyWithLog, mapEmptyStringToNone, toStringWithEmptyDefault}
import io.circe.generic.extras.ConfiguredJsonCodec

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.util.Try

@ConfiguredJsonCodec sealed trait TypeSpecificData {
  val isSubprocess = this match {
    case _: ScenarioSpecificData => false
    case _: FragmentSpecificData => true
  }

  def toMap: Map[String, String] = {
    this match {
      case data: ScenarioSpecificData => data match {
        case StreamMetaData(parallelism, spillStateToDisk, useAsyncInterpretation, checkpointIntervalInSeconds) =>
          Map(
            "parallelism" -> toStringWithEmptyDefault(parallelism),
            "spillStateToDisk" -> toStringWithEmptyDefault(spillStateToDisk),
            "useAsyncInterpretation" -> toStringWithEmptyDefault(useAsyncInterpretation),
            "checkpointIntervalInSeconds" -> toStringWithEmptyDefault(checkpointIntervalInSeconds),
          )
        case LiteStreamMetaData(parallelism) =>
          Map("parallelism" -> toStringWithEmptyDefault(parallelism))
        case RequestResponseMetaData(slug) =>
          Map("slug" -> slug.getOrElse(""))
      }
      case FragmentSpecificData(docsUrl) =>
        Map("docsUrl" -> docsUrl.getOrElse(""))
    }
  }

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

case class FragmentSpecificData(docsUrl: Option[String] = None) extends TypeSpecificData

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

}

object StreamMetaData {
  private val parallelismName = "parallelism"
  private val spillStateToDiskName = "spillStateToDisk"
  private val useAsyncInterpretationName = "useAsyncInterpretation"
  private val checkpointIntervalInSecondsName = "checkpointIntervalInSeconds"

  def apply(properties: Map[String, String]): StreamMetaData = {
    StreamMetaData(
      parallelism = properties.get(parallelismName).flatMap(convertPropertyWithLog(_, _.toInt, parallelismName)),
      spillStateToDisk = properties.get(spillStateToDiskName).flatMap(convertPropertyWithLog(_, _.toBoolean, spillStateToDiskName)),
      useAsyncInterpretation = properties.get(useAsyncInterpretationName).flatMap(convertPropertyWithLog(_, _.toBoolean, useAsyncInterpretationName)),
      checkpointIntervalInSeconds = properties.get(checkpointIntervalInSecondsName).flatMap(convertPropertyWithLog(_, _.toLong, checkpointIntervalInSecondsName))
    )
  }
}

// TODO: parallelism is fine? Maybe we should have other method to adjust number of workers?
case class LiteStreamMetaData(parallelism: Option[Int] = None) extends ScenarioSpecificData

object LiteStreamMetaData {
  private val parallelismName = "parallelism"

  def apply(properties: Map[String, String]): LiteStreamMetaData = {
    LiteStreamMetaData(
      parallelism = properties.get(parallelismName).flatMap(convertPropertyWithLog(_, _.toInt, parallelismName))
    )
  }
}

case class RequestResponseMetaData(slug: Option[String]) extends ScenarioSpecificData

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

  def convertPropertyWithLog[T](value: String, converter: String => T, propertyName: String): Option[T] = {
    Try(converter(value))
      .map(Some(_))
      .recover {
        case _: IllegalArgumentException => None
          // TODO: Add logger or remove logging
          // logger.debug(s"Could not convert property $propertyName with value \'$value\' to desired type.")
      }
      .get
  }
}
