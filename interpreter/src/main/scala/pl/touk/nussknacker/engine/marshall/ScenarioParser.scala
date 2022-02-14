package pl.touk.nussknacker.engine.marshall

import cats.data.{Validated, ValidatedNel}
import io.circe.Json
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess

object ScenarioParser {

  def parseUnsafe(jsonString: String): EspProcess = {
    parse(jsonString).valueOr(err => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", "")))
  }

  def parse(jsonString: String): ValidatedNel[String, EspProcess] =
    Validated.fromEither(CirceUtil.decodeJson[Json](jsonString)).leftMap(_.getMessage).toValidatedNel[String, Json]
      .andThen(ProcessMarshaller.fromJson(_).toValidatedNel)
      .andThen(ProcessCanonizer.uncanonize(_).leftMap(_.map(_.toString)))

}

