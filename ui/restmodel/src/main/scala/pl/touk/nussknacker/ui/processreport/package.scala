package pl.touk.nussknacker.ui

import io.circe.generic.JsonCodec

package object processreport {
  case class RawCount(all: Long, errors: Long)

  @JsonCodec case class NodeCount(all: Long, errors: Long, subprocessCounts: Map[String, NodeCount] = Map())
}
