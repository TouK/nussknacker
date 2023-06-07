package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

@JsonCodec case class LayoutData(x: Long, y: Long)

// todo: MetaData should hold ProcessName as id
@ConfiguredJsonCodec case class MetaData(id: String,
                                         scenarioType: String,
                                         additionalFields: ProcessAdditionalFields) {
  def isSubprocess: Boolean = typeSpecificData.isSubprocess

  def typeSpecificData: TypeSpecificData = {
    scenarioType match {
      case "StreamMetaData" =>
        val properties = additionalFields.properties
        StreamMetaData(
          parallelism = properties.get("parallelism").filterNot(_.isEmpty).map(_.toInt), // TODO: błędne wartości do none
          spillStateToDisk = properties.get("spillStateToDisk").filterNot(_.isEmpty).map(_.toBoolean),
          useAsyncInterpretation = properties.get("useAsyncInterpretation").filterNot(_.isEmpty).map(_.toBoolean),
          checkpointIntervalInSeconds = properties.get("checkpointIntervalInSeconds").filterNot(_.isEmpty).map(_.toLong)
        )
      case _ => ???
    }
  }

  def withTypeSpecificData(typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(id, typeSpecificData)
  }
}

object MetaData {
  def apply(id: String, typeSpecificData: TypeSpecificData, additionalFields: ProcessAdditionalFields): MetaData = {
    MetaData(id = id, scenarioType = typeSpecificData.typeName, additionalFields = additionalFields.copy(
      properties = additionalFields.properties ++ typeSpecificData.toMap
    ))
  }
  def apply(id: String, typeSpecificData: TypeSpecificData): MetaData = {
    MetaData(id = id, scenarioType = typeSpecificData.typeName, additionalFields = ProcessAdditionalFields.empty.copy(
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
        case LiteStreamMetaData(parallelism) => ???
        case RequestResponseMetaData(slug) => ???
      }
      case FragmentSpecificData(docsUrl) =>
        Map("docsUrl" -> docsUrl.getOrElse(""))
    }
  }

  def typeName: String = {
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

// TODO: rename to FlinkStreamMetaData
case class StreamMetaData(parallelism: Option[Int] = None,
                          //we assume it's safer to spill state to disk and fix performance than to fix heap problems...
                          spillStateToDisk: Option[Boolean] = Some(true),
                          useAsyncInterpretation: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends ScenarioSpecificData {

  def checkpointIntervalDuration  : Option[Duration]= checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))

}

// TODO: parallelism is fine? Maybe we should have other method to adjust number of workers?
case class LiteStreamMetaData(parallelism: Option[Int] = None) extends ScenarioSpecificData

case class RequestResponseMetaData(slug: Option[String]) extends ScenarioSpecificData

case class ProcessAdditionalFields(description: Option[String],
                                   properties: Map[String, String])

object ProcessAdditionalFields {

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                                     properties: Option[Map[String, String]])

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  =  deriveConfiguredDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.properties.getOrElse(Map())))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveConfiguredEncoder

  // TODO: check if is needed
  val empty: ProcessAdditionalFields = {
    ProcessAdditionalFields(None, Map.empty)
  }
}
