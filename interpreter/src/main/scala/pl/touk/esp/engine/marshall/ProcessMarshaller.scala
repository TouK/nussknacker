package pl.touk.esp.engine.marshall

import argonaut._
import Argonaut._
import argonaut.PrettyParams
import argonaut.derive._
import cats.data.{Validated, ValidatedNel}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessUnmarshallError._
import pl.touk.esp.engine.marshall.ProcessValidationError.ProcessUncanonizationError

object ProcessMarshaller {

  import cats.std.list._
  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  // Without this nested lists were serialized to colon(head, tail) instead of json array
  private implicit lazy val listOfCanonicalNodeEncoder: EncodeJson[List[CanonicalNode]] = ListEncodeJson[CanonicalNode]
  private implicit lazy val listOfCanonicalNodeDecoder: DecodeJson[List[CanonicalNode]] = CanBuildFromDecodeJson[CanonicalNode, List]

  def toJson(node: EspProcess, prettyParams: PrettyParams) : String = {
    val canonical = ProcessCanonizer.canonize(node)
    toJson(canonical, prettyParams)
  }

  def toJson(canonical: CanonicalProcess, prettyParams: PrettyParams): String = {
    canonical.asJson.pretty(prettyParams.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(json: String): ValidatedNel[ProcessUnmarshallError, EspProcess] = {
    decode(json).toValidatedNel[ProcessUnmarshallError, CanonicalProcess] andThen { canonical =>
      ProcessCanonizer.uncanonize(canonical).leftMap(_.map[ProcessUnmarshallError](ProcessUncanonizationError))
    }
  }

  def decode(json: String): Validated[ProcessJsonDecodeError, CanonicalProcess] = {
    Validated.fromEither(json.decodeEither[CanonicalProcess]).leftMap(ProcessJsonDecodeError)
  }

}