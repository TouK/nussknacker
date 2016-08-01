package pl.touk.esp.engine.marshall

import cats.data.{Validated, ValidatedNel, Xor}
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

object ProcessMarshaller {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  // Without this nested lists were serialized to colon(head, tail) instead of json array
  private implicit lazy val listOfCanonicalNodeEncoder: EncodeJson[List[CanonicalNode]] = ListEncodeJson[CanonicalNode]
  private implicit lazy val listOfCanonicalNodeDecoder: DecodeJson[List[CanonicalNode]] = CanBuildFromDecodeJson[CanonicalNode, List]

  def toJson(node: EspProcess) : String = {
    val flatten = ProcessCanonizer.canonize(node)
    flatten.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(value: String): ValidatedNel[ProcessUnmarshallError, EspProcess] = {
    Validated.fromEither(value.decodeEither[CanonicalProcess]).leftMap[ProcessUnmarshallError](ProcessJsonDecodeError).toValidatedNel andThen
      (ProcessCanonizer.uncanonize(_: CanonicalProcess).leftMap(_.map[ProcessUnmarshallError](ProcessUncanonizationError))) andThen { unflatten =>
        ProcessCompiler.default.compile(unflatten).map(_ => unflatten).leftMap(_.map[ProcessUnmarshallError](ProcessCompilationError))
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

  case class ProcessUncanonizationError(nested: canonize.ProcessUncanonizationError)
    extends ProcessUnmarshallError {

    override def nodeIds: Set[String] = nested.nodeIds

  }

  case class ProcessCompilationError(nested: compile.ProcessCompilationError)  extends ProcessUnmarshallError {
    override def nodeIds: Set[String] = nested.nodeIds
  }

}

