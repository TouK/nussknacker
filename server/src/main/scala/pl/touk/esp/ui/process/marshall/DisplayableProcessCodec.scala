package pl.touk.esp.ui.process.marshall

import java.time.LocalDateTime

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.graph.node
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}

object DisplayableProcessCodec {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //rzutujemy bo argonaut nie lubi kowariancji...
  implicit val nodeAdditionalFieldsOptCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = {
    CodecJson.derived[Option[NodeAdditionalFields]]
      .asInstanceOf[CodecJson[Option[node.UserDefinedAdditionalNodeFields]]]
  }
  implicit val processAdditionalFieldsOptCodec: CodecJson[Option[UserDefinedProcessAdditionalFields]] = {
    CodecJson.derived[Option[ProcessAdditionalFields]]
      .asInstanceOf[CodecJson[Option[UserDefinedProcessAdditionalFields]]]
  }

  def nodeEncoder: EncodeJson[node.NodeData] = EncodeJson.of[node.NodeData]

  def nodeDecoder: DecodeJson[node.NodeData] = DecodeJson.of[node.NodeData]

  def codec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  def propertiesCodec: CodecJson[ProcessProperties] = CodecJson.derive[ProcessProperties]

  //fixme trzebaby uporzadkowac te wszystke kodeki
  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))
  implicit val commentCodec = CodecJson.derived[Comment]
  implicit val processActivityCodec = CodecJson.derive[ProcessActivity]

}

object UiProcessMarshaller {
  def apply(): ProcessMarshaller = {
    new ProcessMarshaller()(DisplayableProcessCodec.nodeAdditionalFieldsOptCodec, DisplayableProcessCodec.processAdditionalFieldsOptCodec)
  }
}
