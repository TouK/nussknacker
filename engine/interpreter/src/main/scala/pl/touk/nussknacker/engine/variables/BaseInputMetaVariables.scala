package pl.touk.nussknacker.engine.variables

import org.apache.kafka.common.record.TimestampType

/**
  * BaseInputMetaVariables keeps definition of kafka event metadata fields.
  */
trait BaseInputMetaVariables {
  def topic: String
  def partition: Integer
  def offset: java.lang.Long
  def timestamp: java.lang.Long
  def timestampType: TimestampType
  def headers: java.util.Map[String, String]
  def leaderEpoch: Integer
}