package pl.touk.nussknacker.test

import com.networknt.schema.{JsonSchemaFactory, SpecVersion}

object TapirJsonSchemaFactory {

  // Regarding to Tapir's MetaSchemaDraft04, tapir is probably compatible with V4 instead of
  // the default for OpenAPI 3.1.0 (V202012) (https://www.openapis.org/blog/2021/02/18/openapi-specification-3-1-released)
  val instance: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)

}
