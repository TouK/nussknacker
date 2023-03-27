package pl.touk.nussknacker.engine.json.swagger.parser

import io.swagger.v3.oas.models.{Components, OpenAPI}

import scala.jdk.CollectionConverters._

object ParseSwaggerRefSchemas {

  def apply(parseResult: OpenAPI): SwaggerRefSchemas =
    Option(parseResult.getComponents).map(refSchemas).getOrElse(Map.empty)

  private def refSchemas(components: Components): SwaggerRefSchemas =
    Option(components.getSchemas).map(_.asScala).getOrElse(Nil).map { case (name, schema) =>
      s"#/components/schemas/$name" -> schema
    }.toMap
}
