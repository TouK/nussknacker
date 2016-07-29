package pl.touk.esp.engine.marshall

import cats.data.{Validated, ValidatedNel, Xor}
import cats.std.list._
import argonaut._
import Argonaut._
import argonaut.PrettyParams
import argonaut.derive._
import pl.touk.esp.engine._
import pl.touk.esp.engine.compile.ProcessCompiler
import pl.touk.esp.engine.flatgraph.FlatProcess
import pl.touk.esp.engine.flatgraph.flatnode.FlatNode
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessUnmarshallError._

object ProcessMarshaller {

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

  def fromJson(value: String): ValidatedNel[ProcessUnmarshallError, EspProcess] = {
    Validated.fromEither(value.decodeEither[FlatProcess]).leftMap[ProcessUnmarshallError](ProcessJsonDecodeError).toValidatedNel andThen
      (GraphFlattener.unFlatten(_: FlatProcess).leftMap(_.map[ProcessUnmarshallError](ProcessUnflattenError))) andThen { unflatten =>
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

  case class ProcessUnflattenError(nested: GraphUnFlattenError) extends ProcessUnmarshallError {
    override def nodeIds: Set[String] = nested.nodeIds
  }

  case class ProcessCompilationError(nested: compile.ProcessCompilationError)  extends ProcessUnmarshallError {
    override def nodeIds: Set[String] = nested.nodeIds
  }

}

