package pl.touk.esp.ui.core.process.marshall

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.ui.core.process.displayedgraph.DisplayableProcess

object DisplayableProcessCodec {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  def encoder: EncodeJson[DisplayableProcess] = EncodeJson.of[DisplayableProcess]

}
