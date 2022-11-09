package pl.touk.nussknacker.engine.json

import com.fasterxml.jackson.databind.JsonNode
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.internal.ReferenceResolver
import org.everit.json.schema.loader.{SchemaLoader, SpecificationVersion}
import org.json.JSONObject
import pl.touk.nussknacker.engine.jackson.Jackson

import java.net.URI

/**
  * Most of this code is copied from io.confluent.kafka.schemaregistry.json.JsonSchema.rawSchema.
  *
  * We want to keep one standard of parsing string to Schema for each modes and engines
  * without doing not necessary dependencies to confluent package (like registry client).
  */
class JsonSchemaParser(val resolvedReferences: Map[String, String]) {

  private val DefaultSpecificationVersion = SpecificationVersion.DRAFT_7
  private val SchemaDefault = "$schema"
  private val UseDefaults = true

  private val objectMapper = Jackson.newObjectMapper

  def parseSchema(stringSchema: String): Schema = {
    val jsonNode = objectMapper.readTree(stringSchema)
    parseSchema(jsonNode)
  }

  def parseSchema(jsonNode: JsonNode): Schema = {
    val builder = SchemaLoader
      .builder
      .useDefaults(UseDefaults)
      .draftV7Support()

    val spec = Option(jsonNode.get(SchemaDefault))
      .flatMap(sch => Option(sch.asText()))
      .map(SpecificationVersion.lookupByMetaSchemaUrl(_).orElse(DefaultSpecificationVersion))
      .getOrElse(DefaultSpecificationVersion)

    val idUri: Option[URI] = Option(jsonNode.get(spec.idKeyword()))
      .flatMap(key => Option(key.asText))
      .map(ReferenceResolver.resolve(null.asInstanceOf[URI], _))

    idUri.foreach{ id =>
      resolvedReferences.foreach { case (key, value) =>
        val child = ReferenceResolver.resolve(id, key)
        builder.registerSchemaByURI(child, new JSONObject(value))
      }
    }

    val jsonObject = objectMapper.treeToValue(jsonNode, classOf[JSONObject])

    builder
      .schemaJson(jsonObject)
      .build()
      .load()
      .build()
      .asInstanceOf[Schema]
  }

}

object JsonSchemaParser {

  def parseSchema(stringSchema: String): Schema =
    new JsonSchemaParser(Map.empty).parseSchema(stringSchema)

}
