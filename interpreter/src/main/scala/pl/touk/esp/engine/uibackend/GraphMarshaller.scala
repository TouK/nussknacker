package pl.touk.esp.engine.uibackend

import argonaut.Argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import argonaut.{PrettyParams, _}
import cats.data.Validated
import pl.touk.esp.engine.canonicalgraph.canonicalnode

object GraphMarshaller {
  import ArgonautShapeless._

  implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  def toGraphJson(nodes: List[canonicalnode.CanonicalNode]) : String = {
    val graph = GraphConverter.toGraph(nodes)
    graph.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(json: String): Validated[Any, List[canonicalnode.CanonicalNode]] = {
    Validated.fromEither(json.decodeEither[GraphDisplay.Graph]) andThen { decoded =>
      GraphConverter.fromGraph(decoded)
    }
  }

}