package pl.touk.nussknacker.engine.json

import io.circe.Json
import org.everit.json.schema.{
  ArraySchema,
  BooleanSchema,
  CombinedSchema,
  EmptySchema,
  EnumSchema,
  NullSchema,
  NumberSchema,
  ObjectSchema,
  ReferenceSchema,
  Schema,
  StringSchema
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.json.JsonSchemaUtils.jsonToCirce

import scala.collection.compat.immutable.LazyList
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object JsonTemplateFromJsonSchemaDeterminer {

  def jsonTemplateFor(schema: Schema): Expression = {
    val defaultJson = defaultJsonFor(schema)
    Expression.jsonTemplate(defaultJson.getOrElse(Json.obj()).spaces2)
  }

  private def defaultJsonFor(schema: Schema): Option[Json] = schema match {
    case schema: Schema if schema.hasDefaultValue =>
      val defaultValue = schema.getDefaultValue
      Some(jsonToCirce(defaultValue))
    case other =>
      defaultValueBasedOnSchema(other)
  }

  private def defaultValueBasedOnSchema(schema: Schema): Option[Json] = schema match {
    case _: EmptySchema => Some(Json.obj())
    case objSchema: ObjectSchema =>
      val props    = ListMap(objSchema.getPropertySchemas.asScala.toList.sortBy(_._1): _*)
      val required = objSchema.getRequiredProperties.asScala.toSet
      val fields = props.flatMap { case (key, subSchema) =>
        defaultJsonFor(subSchema) match {
          case Some(value)                    => Some(key -> value)
          case None if required.contains(key) => Some(key -> Json.Null)
          case _                              => None
        }
      }
      Some(Json.obj(fields.toSeq: _*))
    case arraySchema: ArraySchema =>
      Option(arraySchema.getAllItemSchema)
        .flatMap(defaultValueBasedOnSchema)
        .map(Json.arr(_))
        .orElse(Some(Json.arr()))
    case combinedSchema: CombinedSchema =>
      val criterion  = combinedSchema.getCriterion
      val subschemas = combinedSchema.getSubschemas.asScala.toSeq
      criterion match {
        case CombinedSchema.ALL_CRITERION =>
          subschemas.collectFirst { case schema: EnumSchema =>
            schema.getPossibleValuesAsList.asScala.headOption.map(jsonToCirce)
          } match {
            case Some(json) =>
              json
            case None =>
              // Merge all defaults
              val mergedFields = subschemas
                .flatMap(schema => defaultJsonFor(schema))
                .collect { case jsonObj if jsonObj.isObject => jsonObj.asObject.get.toMap }
                .foldLeft(Map.empty[String, Json])(_ ++ _)
              Some(Json.fromFields(mergedFields))
          }
        case CombinedSchema.ANY_CRITERION | CombinedSchema.ONE_CRITERION =>
          // Pick the first schema with extractable defaults
          subschemas
            .to(LazyList)
            .flatMap(schema => defaultJsonFor(schema))
            .headOption
        case _ => None
      }
    case refSchema: ReferenceSchema             => defaultJsonFor(refSchema.getReferredSchema)
    case _: StringSchema                        => Some(Json.fromString("#{ '' }"))
    case s: NumberSchema if s.requiresInteger() => Some(Json.fromInt(0))
    case _: NumberSchema                        => Some(Json.fromDoubleOrNull(0.0))
    case _: BooleanSchema                       => Some(Json.fromBoolean(true))
    case s: EnumSchema                          => s.getPossibleValuesAsList.asScala.headOption.map(jsonToCirce)
    case s: NullSchema                          => Some(Json.Null)
  }

}
