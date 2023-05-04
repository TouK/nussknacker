package pl.touk.nussknacker.engine.api

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Try
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.FragmentSpecificData.docsUrlName
import pl.touk.nussknacker.engine.api.LiteStreamMetaData.parallelismLiteName
import pl.touk.nussknacker.engine.api.RequestResponseMetaData.slugName
import pl.touk.nussknacker.engine.api.StreamMetaData.{checkpointIntervalInSecondsName, parallelismFlinkName, spillStateToDiskName, useAsyncInterpretationName}
import pl.touk.nussknacker.engine.api.utils.{convertPropertyWithLog, toStringMap}

@ConfiguredJsonCodec sealed trait TypeSpecificData {
  val isSubprocess = this match {
    case _: ScenarioSpecificData => false
    case _: FragmentSpecificData => true
  }
  val toProperties: Map[String, String]
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
    docsUrl.map(docsUrlName -> _)
  ))
}

object FragmentSpecificData {
  private val docsUrlName = "docsUrl"

  def apply(properties: Map[String, String]): FragmentSpecificData = {
    FragmentSpecificData(
      docsUrl = properties.get(docsUrlName)
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
    parallelism.map(parallelismFlinkName -> _),
    spillStateToDisk.map(spillStateToDiskName -> _),
    useAsyncInterpretation.map(useAsyncInterpretationName -> _),
    checkpointIntervalInSeconds.map(checkpointIntervalInSecondsName -> _)
  ))
}

object StreamMetaData {
  private val parallelismFlinkName = "parallelism"
  private val spillStateToDiskName = "spillStateToDisk"
  private val useAsyncInterpretationName = "useAsyncInterpretation"
  private val checkpointIntervalInSecondsName = "checkpointIntervalInSeconds"

  def apply(properties: Map[String, String]): StreamMetaData = {
    StreamMetaData(
      parallelism = properties.get(parallelismFlinkName).flatMap(convertPropertyWithLog(_, _.toInt, parallelismFlinkName)),
      spillStateToDisk = properties.get(spillStateToDiskName).flatMap(convertPropertyWithLog(_, _.toBoolean, spillStateToDiskName)),
      useAsyncInterpretation = properties.get(useAsyncInterpretationName).flatMap(convertPropertyWithLog(_, _.toBoolean, useAsyncInterpretationName)),
      checkpointIntervalInSeconds = properties.get(checkpointIntervalInSecondsName).flatMap(convertPropertyWithLog(_, _.toLong, checkpointIntervalInSecondsName))
    )
  }
}

// TODO: parallelism is fine? Maybe we should have other method to adjust number of workers?
case class LiteStreamMetaData(parallelism: Option[Int] = None) extends ScenarioSpecificData {
  override val toProperties: Map[String, String] = toStringMap(List(
    parallelism.map(parallelismLiteName -> _)))
}

object LiteStreamMetaData {
  private val parallelismLiteName = "parallelism"

  def apply(properties: Map[String, String]): LiteStreamMetaData = {
    LiteStreamMetaData(
      parallelism = properties.get(parallelismLiteName).flatMap(convertPropertyWithLog(_, _.toInt, parallelismLiteName)))
  }
}

case class RequestResponseMetaData(slug: Option[String]) extends ScenarioSpecificData {
  override val toProperties: Map[String, String] = toStringMap(List(
    slug.map(slugName -> _)))
}

object RequestResponseMetaData {
  private val slugName = "slug"

  def apply(properties: Map[String, String]): RequestResponseMetaData = {
    RequestResponseMetaData(
      slug = properties.get(slugName))
  }
}

object utils extends LazyLogging {

  def toStringMap(seq: Seq[Option[(String, Any)]]): Map[String, String] = {
    seq.flatten
      .map(p => (p._1, p._2.toString))
      .toMap
  }

  def convertPropertyWithLog[T](value: String, converter: String => T, propertyName: String): Option[T] = {
    Try(converter(value)).map(Some(_)).recover {
      case _: IllegalArgumentException =>
        // We allow for invalid values to be persisted. If we cannot convert a string to a desired type, we set it as
        // None in TypeSpecificData and store the invalid value in AdditionalFields.
        logger.debug(s"Could not convert property $propertyName with value \"$value\" to desired type.")
        None
    }.get
  }
}
