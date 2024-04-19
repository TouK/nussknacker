package pl.touk.nussknacker.engine.marshall

import cats.data.{Validated, ValidatedNel}
import io.circe.Json
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.ResourceLoader

object ScenarioParser {

  def parseUnsafe(jsonString: String): CanonicalProcess = {
    parse(jsonString).valueOr(err =>
      throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
    )
  }

  def parse(jsonString: String): ValidatedNel[String, CanonicalProcess] =
    Validated
      .fromEither(CirceUtil.decodeJson[Json](jsonString))
      .leftMap(_.getMessage)
      .toValidatedNel[String, Json]
      .andThen(ProcessMarshaller.fromJson(_).toValidatedNel)

  def loadFromResource(path: String): CanonicalProcess =
    parseUnsafe(ResourceLoader.load(path))

}
