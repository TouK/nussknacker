package pl.touk.http.argonaut

import argonaut.{Json, JsonBigDecimal, JsonDecimal, JsonLong, PrettyParams}
import com.fasterxml.jackson.core.util.{DefaultIndenter, DefaultPrettyPrinter}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.databind.node.{ArrayNode, BooleanNode, DecimalNode, JsonNodeFactory, LongNode, NullNode, ObjectNode, TextNode}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

object JsonMarshaller {

  private val useJacksonJsonMarshallerProperty = "useJacksonJsonMarshaller"

  def prepareDefault(config: Config): JsonMarshaller =
    if (config.hasPath(useJacksonJsonMarshallerProperty) && config.getBoolean(useJacksonJsonMarshallerProperty)) JacksonJsonMarshaller else ArgonautJsonMarshaller

}

trait JsonMarshaller {

  def marshall(json: Json, options: MarshallOptions = MarshallOptions()): Array[Byte]

  def marshallToString(json: Json, options: MarshallOptions = MarshallOptions()): String

}

case class MarshallOptions(pretty: Boolean = false)

object ArgonautJsonMarshaller extends JsonMarshaller {

  override def marshall(json: Json, options: MarshallOptions): Array[Byte] = marshallToString(json, options).getBytes

  override def marshallToString(json: Json, options: MarshallOptions): String = {
    val prettyParams = if (options.pretty) PrettyParams.spaces2 else PrettyParams.nospace
    json.pretty(prettyParams)
  }

}

//This is temporary performance fix, argonaut handles large Json Strings v. ineffectively, in the future we'll move to circe
//and get rid of it
object JacksonJsonMarshaller extends JsonMarshaller {

  private val defaultObjectMapper = new ObjectMapper()
  private val prettyPrintObjectMapper = {
    val prettyPrinter = new DefaultPrettyPrinter()
    prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
    new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDefaultPrettyPrinter(prettyPrinter)
  }

  override def marshallToString(json: Json, options: MarshallOptions): String = new String(marshall(json, options))

  override def marshall(json: Json, options: MarshallOptions): Array[Byte] = objectMapperFor(options).writeValueAsBytes(convertToJackson(json))

  def convertToJackson(json: Json): JsonNode = {
    json.fold(
      NullNode.instance,
      bool => BooleanNode.valueOf(bool),
      {
        case JsonBigDecimal(value) => new DecimalNode(value.bigDecimal)
        //TODO: is it ok performance wise?
        case e: JsonDecimal => new DecimalNode(e.toBigDecimal.bigDecimal)
        case JsonLong(value) => new LongNode(value)
      },
      str => new TextNode(str),
      array => {
        val node = new ArrayNode(JsonNodeFactory.instance, array.length)
        array.foreach(j => node.add(convertToJackson(j)))
        node
      },
      obj => new ObjectNode(JsonNodeFactory.instance, obj.toMap.mapValues(convertToJackson).asJava)
    )
  }

  private def objectMapperFor(options: MarshallOptions): ObjectMapper = {
    if (options.pretty) prettyPrintObjectMapper else defaultObjectMapper
  }
}
