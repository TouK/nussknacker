package pl.touk.nussknacker.openapi.extractor

import java.util.Collections
import io.circe.Json
import pl.touk.nussknacker.engine.json.swagger.decode.FromJsonSchemaBasedDecoder
import pl.touk.nussknacker.engine.json.swagger.{SwaggerArray, SwaggerTyped}

object HandleResponse {

  def apply(res: Option[Json], responseType: SwaggerTyped): AnyRef = res match {
    case Some(json) =>
      FromJsonSchemaBasedDecoder.decode(json, responseType)
    case None =>
      responseType match {
        case _: SwaggerArray => Collections.EMPTY_LIST
        case _               => null
      }
  }

}
