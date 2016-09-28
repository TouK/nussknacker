package pl.touk.esp.ui.process.marshall

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}

object DisplayableProcessCodec {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  def nodeEncoder: EncodeJson[NodeData] = EncodeJson.of[NodeData]

  def nodeDecoder: DecodeJson[NodeData] = DecodeJson.of[NodeData]

  def codec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  def propertiesCodec: CodecJson[ProcessProperties] = CodecJson.derive[ProcessProperties]

}
