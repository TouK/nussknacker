package pl.touk.nussknacker.engine.variables

import org.apache.kafka.common.record.TimestampType

/**
  * BaseInputMetaVariables keeps definition of kafka event metadata fields.
  * Required by [[pl.touk.nussknacker.engine.types.TypesInformationExtractor]] to extract methods to suggest in FE component.
  */
trait BaseKafkaInputMetaVariables {
  def topic: String
  def partition: Integer
  def offset: java.lang.Long
  def timestamp: java.lang.Long
  def timestampType: TimestampType
  def headers: java.util.Map[String, String]
  def leaderEpoch: Integer
}