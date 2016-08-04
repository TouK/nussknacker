package pl.touk.esp.engine.graph

object sink {

  case class Parameter(name: String, value: String)

  case class SinkRef(typ: String, parameters: List[Parameter])

}
