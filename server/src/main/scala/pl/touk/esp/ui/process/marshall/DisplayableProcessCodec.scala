package pl.touk.esp.ui.process.marshall

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.displayedgraph.displayablenode.DisplayableNode

object DisplayableProcessCodec {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  def encoder: EncodeJson[DisplayableProcess] = EncodeJson.of[DisplayableProcess]

  def decoder: DecodeJson[DisplayableProcess] = DecodeJson.of[DisplayableProcess]

  def nodeEncoder: EncodeJson[DisplayableNode] = EncodeJson.of[DisplayableNode]

  def nodeDecoder: DecodeJson[DisplayableNode] = DecodeJson.of[DisplayableNode]

}
