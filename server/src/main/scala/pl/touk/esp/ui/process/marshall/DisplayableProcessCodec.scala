package pl.touk.esp.ui.process.marshall

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess

object DisplayableProcessCodec {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  def encoder: EncodeJson[DisplayableProcess] = EncodeJson.of[DisplayableProcess]

  def decoder: DecodeJson[DisplayableProcess] = DecodeJson.of[DisplayableProcess]

}
