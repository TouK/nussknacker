package pl.touk.nussknacker.engine.json.encode

import io.circe.Json
import io.circe.syntax._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonTypedMap
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

class LazyJsonTypedMapEncoder extends ToJsonEncoder {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = {
    case JsonTypedMap(jsonObject, definition, _) =>
      encode(jsonObject.filterKeys(definition.fieldSwaggerTypeByKey(_).isDefined).asJson)
  }

}
