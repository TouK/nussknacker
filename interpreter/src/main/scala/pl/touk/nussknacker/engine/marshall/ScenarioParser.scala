package pl.touk.nussknacker.engine.marshall

import cats.data.{Validated, ValidatedNel}
import io.circe.Json
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ProcessJsonDecodeError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import io.circe.syntax._

object ScenarioParser {

  def parseUnsafe(jsonString: String): EspProcess = {
    parse(jsonString).valueOr(err => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", "")))
  }

  def parse(jsonString: String): ValidatedNel[ProcessCompilationError, EspProcess] =
    Validated.fromEither(CirceUtil.decodeJson[Json](jsonString)).leftMap(_.getMessage)
      .leftMap(ProcessJsonDecodeError)
      .toValidatedNel[ProcessCompilationError, Json]
      .andThen(parse)

  def parse(json: Json): ValidatedNel[ProcessCompilationError, EspProcess] =
    ProcessMarshaller
      .fromJson(json)
      .leftMap(ProcessJsonDecodeError)
      .toValidatedNel[ProcessCompilationError, CanonicalProcess]
      .andThen(ProcessCanonizer.uncanonize)

  def toJson(process: EspProcess): Json =
    ProcessCanonizer.canonize(process).asJson

}

