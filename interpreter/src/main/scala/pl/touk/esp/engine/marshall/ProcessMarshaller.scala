package pl.touk.esp.engine.marshall

import cats.data.{OneAnd, Validated, ValidatedNel, Xor}
import cats.std.list._
import argonaut._
import Argonaut._
import argonaut.PrettyParams
import argonaut.derive._
import pl.touk.esp.engine.{canonize, _}
import pl.touk.esp.engine.compile.ProcessCompiler
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessUnmarshallError._
import pl.touk.esp.engine.marshall.ProcessValidationError._

object ProcessMarshaller {

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
      validate(canonical).leftMap(_.map[ProcessUnmarshallError](identity))
    }
  }

  def decode(json: String): Validated[ProcessJsonDecodeError, CanonicalProcess] = {
    Validated.fromEither(json.decodeEither[CanonicalProcess]).leftMap(ProcessJsonDecodeError)
  }

  def validate(canonical: CanonicalProcess): ValidatedNel[ProcessValidationError, EspProcess] = {
    ProcessCanonizer.uncanonize(canonical).leftMap(_.map[ProcessValidationError](ProcessUncanonizationError)) andThen { unflatten =>
      ProcessCompiler.default.compile(unflatten).map(_ => unflatten).leftMap(_.map[ProcessValidationError](ProcessCompilationError))
    }
  }

}

sealed trait ProcessUnmarshallError {

  def nodeIds: Set[String]

}

object ProcessUnmarshallError {

  case class ProcessJsonDecodeError(msg: String) extends ProcessUnmarshallError {
    override val nodeIds: Set[String] = Set.empty
  }

}

sealed trait ProcessValidationError extends ProcessUnmarshallError

object ProcessValidationError {

  case class ProcessUncanonizationError(nested: canonize.ProcessUncanonizationError)
    extends ProcessValidationError {

    override def nodeIds: Set[String] = nested.nodeIds

  }

  case class ProcessCompilationError(nested: compile.ProcessCompilationError)  extends ProcessValidationError {
    override def nodeIds: Set[String] = nested.nodeIds
  }

}