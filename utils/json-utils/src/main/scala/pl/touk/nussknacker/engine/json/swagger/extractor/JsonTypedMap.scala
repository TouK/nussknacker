package pl.touk.nussknacker.engine.json.swagger.extractor

import io.circe.syntax.EncoderOps
import io.circe.{Json, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct.JsonToObjectError
import pl.touk.nussknacker.engine.util.cache.LazyMap
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.jdk.CollectionConverters._

final case class JsonTypedMap(jsonObject: JsonObject, definition: SwaggerObject, path: String = "")
    extends LazyMap[String, Any](
      jsonObject.keys.filter(definition.fieldSwaggerTypeByKey(_).isDefined).toSet.asJava,
      key =>
        definition.fieldSwaggerTypeByKey(key) match {
          case Some(swaggerType) =>
            JsonToNuStruct(jsonObject(key).getOrElse(Json.Null), swaggerType, if (path.isEmpty) key else s"$path.$key")
          case None => JsonToObjectError(jsonObject.asJson, definition, path)
        }
    ) {

  def materialize: TypedMap =
    TypedMap(this.asScala.toMap.mapValuesNow {
      case x: JsonTypedMap => x.materialize
      case x               => x
    })

}
