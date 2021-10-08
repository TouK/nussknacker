package pl.touk.nussknacker.engine.api

import java.util.concurrent.TimeUnit
import io.circe.generic.extras.ConfiguredJsonCodec
import CirceUtil._
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValue

import scala.concurrent.duration.Duration

case class ProcessAdditionalFields(description: Option[String],
                                   properties: Map[String, String])

object ProcessAdditionalFields {

  //TODO: is this currently needed?
  private case class OptionalProcessAdditionalFields(description: Option[String],
                                     properties: Option[Map[String, String]])

  implicit val circeDecoder: Decoder[ProcessAdditionalFields]
  =  deriveConfiguredDecoder[OptionalProcessAdditionalFields].map(opp => ProcessAdditionalFields(opp.description, opp.properties.getOrElse(Map())))

  implicit val circeEncoder: Encoder[ProcessAdditionalFields] = deriveConfiguredEncoder
}

@JsonCodec case class LayoutData(x: Long, y: Long)

// todo: MetaData should hold ProcessName as id
@ConfiguredJsonCodec case class MetaData(id: String,
                    typeSpecificData: TypeSpecificData,
                    isSubprocess: Boolean = false,
                    additionalFields: Option[ProcessAdditionalFields] = None,
                    subprocessVersions: Map[String, Long] = Map.empty)

@ConfiguredJsonCodec sealed trait TypeSpecificData

case class StreamMetaData(parallelism: Option[Int] = None,
                          //we assume it's safer to spill state to disk and fix performance than to fix heap problems...
                          spillStateToDisk: Option[Boolean] = Some(true),
                          useAsyncInterpretation: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends TypeSpecificData {

  def checkpointIntervalDuration  : Option[Duration]= checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))

  def shouldUseAsyncInterpretation(implicit defaultValue: DefaultAsyncInterpretationValue) : Boolean = useAsyncInterpretation.getOrElse(defaultValue.value)

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

case class StandaloneMetaData(path: Option[String]) extends TypeSpecificData
