package pl.touk.nussknacker.engine.avro.decode

import org.apache.avro.generic.{GenericArray, GenericEnumSymbol, GenericFixed, GenericRecord, IndexedRecord}
import org.apache.avro.util.Utf8

import java.util
import scala.collection.JavaConverters._

object BestEffortAvroDecoder {
  //Simple decode avro data to scala type
  def decode(value: Any): Any = value match {
    case record: IndexedRecord =>
      record.getSchema.getFields.asScala.zipWithIndex.map{ case (field, index) =>
        field.name() -> decode(record.get(index))
      }.toMap
    case en: GenericEnumSymbol[_] =>
      en.toString
    case fix: GenericFixed =>
      fix.bytes()
    case map: util.Map[String@unchecked, _] => //type: map
      map.asScala.map{ case (key, value) =>
        key -> decode(value)
      }.toMap
    case array: util.List[_] => //type: array
      array.asScala.map(decode).toList
    case str: Utf8 =>
      str.toString
    case _ => value
  }
}
