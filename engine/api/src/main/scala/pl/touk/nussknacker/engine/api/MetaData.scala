package pl.touk.nussknacker.engine.api

import java.util.concurrent.TimeUnit

import argonaut.Argonaut.jdecode3L
import argonaut.{DecodeJson, EncodeJson}
import io.circe.generic.extras.ConfiguredJsonCodec
import CirceUtil._
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._

import scala.concurrent.duration.Duration

case class ProcessAdditionalFields(description: Option[String],
                                   groups: Set[Group],
                                   properties: Map[String, String])

object ProcessAdditionalFields {
  import argonaut.ArgonautShapeless._

  implicit val decoder: DecodeJson[ProcessAdditionalFields] = {
    // this is needed for parsing json that does not have groups or properties fields
    jdecode3L((description: Option[String], groups: Option[Set[Group]], properties: Option[Map[String, String]]) =>
      ProcessAdditionalFields(description, groups.getOrElse(Set.empty), properties.getOrElse(Map.empty))
    )("description", "groups", "properties").map(identity[ProcessAdditionalFields])
  }

  implicit val encoder: EncodeJson[ProcessAdditionalFields] = {
    EncodeJson.derive[ProcessAdditionalFields]
  }

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                     groups: Option[Set[Group]],
                                     properties: Option[Map[String, String]])

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  = deriveDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.groups.getOrElse(Set()), opp.properties.getOrElse(Map())))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveEncoder
}

@JsonCodec case class Group(id: String, nodes: Set[String])

// todo: MetaData should hold ProcessName as id
@ConfiguredJsonCodec case class MetaData(id: String,
                    typeSpecificData: TypeSpecificData,
                    isSubprocess: Boolean = false,
                    additionalFields: Option[ProcessAdditionalFields] = None,
                    subprocessVersions: Map[String, Long] = Map.empty)

@ConfiguredJsonCodec sealed trait TypeSpecificData {
  def allowLazyVars : Boolean
}

case class StreamMetaData(parallelism: Option[Int] = None,
                          splitStateToDisk: Option[Boolean] = None,
                          useAsyncInterpretation: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends TypeSpecificData {
  
  def checkpointIntervalDuration  : Option[Duration]= checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))

  val shouldUseAsyncInterpretation : Boolean = useAsyncInterpretation.getOrElse(false)

  override val allowLazyVars: Boolean = !shouldUseAsyncInterpretation

}

object StreamMetaData {
  def empty(isSubprocess: Boolean): StreamMetaData = {
    if (isSubprocess) {
      StreamMetaData(parallelism = None)
    } else {
      StreamMetaData(parallelism = Some(1))
    }
  }
}

case class StandaloneMetaData(path: Option[String]) extends TypeSpecificData {
  override val allowLazyVars: Boolean = true
}