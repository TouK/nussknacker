package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, HCursor}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.TypeSpecificUtils._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.Try

@JsonCodec case class LayoutData(x: Long, y: Long)

// todo: MetaData should hold ProcessName as id
@ConfiguredJsonCodec(encodeOnly = true) case class MetaData(id: String,
                                                            additionalFields: ProcessAdditionalFields) {
  def isSubprocess: Boolean = typeSpecificData.isSubprocess

  def typeSpecificData: TypeSpecificData = {
    additionalFields.metaDataType match {
      case "StreamMetaData" => StreamMetaData(additionalFields.properties)
      case "LiteStreamMetaData" => LiteStreamMetaData(additionalFields.properties)
      case "RequestResponseMetaData" => RequestResponseMetaData(additionalFields.properties)
      case "FragmentSpecificData" => FragmentSpecificData(additionalFields.properties)
    }
  }

  def withTypeSpecificData(typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(id, typeSpecificData)
  }
}

object MetaData {

  private val actualDecoder: Decoder[MetaData] = deriveConfiguredDecoder[MetaData]

  val legacyDecoder: Decoder[MetaData] = {
    def legacyProcessAdditionalFieldsDecoder(scenarioType: String): Decoder[ProcessAdditionalFields] =
      (c: HCursor) => for {
        id <- c.downField("description").as[Option[String]]
        properties <- c.downField("properties").as[Option[Map[String, String]]]
      } yield {
        ProcessAdditionalFields(id, properties.getOrElse(Map.empty), scenarioType)
      }

    (c: HCursor) => for {
      id <- c.downField("id").as[String]
      typeSpecificData <- c.downField("typeSpecificData").as[TypeSpecificData]
      additionalFields <- c.downField("additionalFields")
        .as[Option[ProcessAdditionalFields]](
          io.circe.Decoder.decodeOption(
            legacyProcessAdditionalFieldsDecoder(typeSpecificData.metaDataType)
          )
        ).map(_.getOrElse(ProcessAdditionalFields.empty(typeSpecificData.metaDataType)))
    } yield {
      MetaData(id, typeSpecificData, additionalFields)
    }
  }

  implicit val decoder: Decoder[MetaData] = actualDecoder or legacyDecoder

  def apply(id: String, typeSpecificData: TypeSpecificData, additionalFields: ProcessAdditionalFields): MetaData = {
    MetaData(id = id, additionalFields = additionalFields.copy(
      properties = additionalFields.properties ++ typeSpecificData.toMap
    ))
  }

  def apply(id: String, typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(
      id = id,
      additionalFields = ProcessAdditionalFields.empty(typeSpecificData.metaDataType).copy(
        properties = typeSpecificData.toMap
      ))
  }
}

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
            "parallelism" -> parallelism.map(_.toString).getOrElse(""),
            "spillStateToDisk" -> spillStateToDisk.map(_.toString).getOrElse(""),
            "useAsyncInterpretation" -> useAsyncInterpretation.map(_.toString).getOrElse(""),
            "checkpointIntervalInSeconds" -> checkpointIntervalInSeconds.map(_.toString).getOrElse(""),
          )
        case LiteStreamMetaData(parallelism) =>
          Map("parallelism" -> parallelism.map(_.toString).getOrElse(""))
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
    RequestResponseMetaData(slug = properties.get(slugName))
  }
}

case class ProcessAdditionalFields(description: Option[String],
                                   properties: Map[String, String],
                                   metaDataType: String) {

  def typeSpecificProperties: TypeSpecificData = {
    metaDataType match {
      case "StreamMetaData" => StreamMetaData(properties)
      case "LiteStreamMetaData" => LiteStreamMetaData(properties)
      case "RequestResponseMetaData" => RequestResponseMetaData(properties)
      case "FragmentSpecificData" => FragmentSpecificData(properties)
      case _ => throw new IllegalStateException("Unrecognized metadata type.")
    }
  }

}

object ProcessAdditionalFields {

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                                     properties: Option[Map[String, String]],
                                                     metaDataType: String)

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  = deriveConfiguredDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.properties.getOrElse(Map()), opp.metaDataType))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveConfiguredEncoder

  // TODO: check if is needed
  def empty(metaDataType: String): ProcessAdditionalFields = {
    ProcessAdditionalFields(None, Map.empty, metaDataType)
  }
}

object TypeSpecificUtils {

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
        case _: IllegalArgumentException =>
          // We allow for invalid values to be persisted. If we cannot convert a string to a desired type, we set it as
          // None in TypeSpecificData and store the invalid value in AdditionalFields.
          // TODO: Add logger or remove logging
          // logger.debug(s"Could not convert property $propertyName with value \'$value\' to desired type.")
          None
      }
      .get
  }
}
