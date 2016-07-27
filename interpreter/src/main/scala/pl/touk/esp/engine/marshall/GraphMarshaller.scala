package pl.touk.esp.engine.marshall

import cats.data.Xor
import argonaut._
import Argonaut._
import argonaut.PrettyParams
import argonaut.derive._
import pl.touk.esp.engine.flatgraph.FlatProcess
import pl.touk.esp.engine.flatgraph.flatnode.FlatNode
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.validate.GraphValidator

object GraphMarshaller {

  import ArgonautShapeless._

  implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  // Without this nested lists were serialized to colon(head, tail) instead of json array
  implicit lazy val listOfFlatNodeEncoder: EncodeJson[List[FlatNode]] = ListEncodeJson[FlatNode]
  implicit lazy val listOfFlatNodeDecoder: DecodeJson[List[FlatNode]] = CanBuildFromDecodeJson[FlatNode, List]

  def toJson(node: EspProcess) : String = {
    val flatten = GraphFlattener.flatten(node)
    flatten.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(value: String): Xor[Any, EspProcess] = {
    for {
      decoded <- Xor.fromEither(value.decodeEither[FlatProcess])
      unFlatten <- GraphFlattener.unFlatten(decoded)
      _ <- GraphValidator.validate(unFlatten).toXor
    } yield unFlatten
  }

}