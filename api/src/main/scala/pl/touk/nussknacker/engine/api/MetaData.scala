package pl.touk.nussknacker.engine.api

import java.util.concurrent.TimeUnit
import io.circe.generic.extras.ConfiguredJsonCodec
import CirceUtil._
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValue
import pl.touk.nussknacker.engine.api.process.VersionId

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
                    additionalFields: Option[ProcessAdditionalFields] = None,
                    subprocessVersions: Map[String, Long] = Map.empty) { //TODO: field subprocessVersions is deprecate - to remove
  val isSubprocess: Boolean = typeSpecificData.isSubprocess
}

@ConfiguredJsonCodec sealed trait TypeSpecificData {
  val isSubprocess = this match {
    case _: ScenarioSpecificData => false
    case _: FragmentSpecificData => true
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

  def shouldUseAsyncInterpretation(implicit defaultValue: DefaultAsyncInterpretationValue) : Boolean = useAsyncInterpretation.getOrElse(defaultValue.value)

}

// TODO: parallelism is fine? Maybe we should have other method to adjust number of workers?
case class LiteStreamMetaData(parallelism: Option[Int] = None) extends ScenarioSpecificData

case class RequestResponseMetaData(path: Option[String]) extends ScenarioSpecificData
