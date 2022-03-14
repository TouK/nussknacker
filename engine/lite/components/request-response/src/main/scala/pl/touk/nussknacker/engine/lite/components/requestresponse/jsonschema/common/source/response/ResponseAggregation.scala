package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source.response

import io.circe.Json


object ResponseAggregation {
  case object Head extends ResponseAggregation {
    override def transform(processId: String): List[Any] => Json = ResponseAggregationStrategy.head(processId)
  }

  case object Array extends ResponseAggregation {
    override def transform(processId: String): List[Any] => Json = ResponseAggregationStrategy.array(processId)
  }

  // don't add type annotations! :P (https://github.com/scala/bug/issues/10626)
  final val head = "Single sink"
  final val array = "All as list"

  final val headExpression = "Single sink"
  final val arrayExpression = "All as list"

  def apply(name: String): ResponseAggregation = name match {
    case `head`  => Head
    case `array` => Array
  }
}

sealed trait ResponseAggregation {
  def transform(processId: String): List[Any] => Json
}