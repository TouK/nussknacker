package pl.touk.nussknacker.engine.schemedkafka.encode

import io.circe.Json
import pl.touk.nussknacker.engine.util.json.ToJsonEncoderCustomisation

import scala.jdk.CollectionConverters._

class AvroToJsonEncoderCustomisation extends ToJsonEncoderCustomisation {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = {
    case e: org.apache.avro.generic.GenericRecord =>
      // TODO_PAWEL to jest jakies toMap zrobione, znalezione, mozna sobie tak zrobic na tym naszym zrodle i bedzie fajnie
      // TODO_PAWEL czy to zadziala jak jest gleboki ten obiekt?, moze to jest juz glebokie przez to ze tu sie jakas rekurencja dzieje
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      encode(map)
  }

}
