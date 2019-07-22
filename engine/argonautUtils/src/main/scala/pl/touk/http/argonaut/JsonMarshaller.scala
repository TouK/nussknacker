package pl.touk.http.argonaut

import argonaut.{Json, JsonBigDecimal, JsonDecimal, JsonLong}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.{ArrayNode, BooleanNode, DecimalNode, JsonNodeFactory, LongNode, NullNode, ObjectNode, TextNode}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

object JsonMarshaller {

  private val useJacksonJsonMarshallerProperty = "useJacksonJsonMarshaller"

  def prepareDefault(config: Config): JsonMarshaller =
    if (config.hasPath(useJacksonJsonMarshallerProperty) && config.getBoolean(useJacksonJsonMarshallerProperty)) JacksonJsonMarshaller else ArgonautJsonMarshaller

}

trait JsonMarshaller {

  def marshall(json: Json): Array[Byte]

  def marshallToString(json:Json): String

}

object ArgonautJsonMarshaller extends JsonMarshaller {

  override def marshall(json: Json): Array[Byte] = marshallToString(json).getBytes

  override def marshallToString(json: Json): String = json.nospaces

}

//This is temporary performance fix, argonaut handles large Json Strings v. ineffectively, in the future we'll move to circe
//and get rid of it
object JacksonJsonMarshaller extends JsonMarshaller {

  //TODO: configuration?
  private val om = new ObjectMapper()

  override def marshallToString(json: Json): String = new String(marshall(json))

  override def marshall(json: Json): Array[Byte] = om.writeValueAsBytes(convertToJackson(json))

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

}
