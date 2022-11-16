package pl.touk.nussknacker.engine.json

import org.everit.json.schema.{CombinedSchema, NullSchema, Schema}

object JsonSchemaImplicits {
  import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

  implicit class JsonSchema(schema: Schema) {
    def isNullAllowed: Boolean = {
      def isNullSchema(sch: Schema) = sch match {
        case _: NullSchema => true
        case _ => false
      }

      val result = schema match {
        case combined: CombinedSchema => combined.getSubschemas.asScala.exists(isNullSchema)
        case sch: Schema => isNullSchema(sch)
      }

      result
    }
  }
}
