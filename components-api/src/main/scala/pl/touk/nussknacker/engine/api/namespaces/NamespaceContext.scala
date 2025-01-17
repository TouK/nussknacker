package pl.touk.nussknacker.engine.api.namespaces

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}

sealed trait NamespaceContext extends EnumEntry with EnumEntry.Uncapitalised

object NamespaceContext extends Enum[NamespaceContext] {
  override def values = findValues

  case object Kafka   extends NamespaceContext
  case object Metrics extends NamespaceContext
  case object Flink   extends NamespaceContext
}
