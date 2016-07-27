package pl.touk.esp.engine.graph

object source {

  case class Parameter(name: String, value: String)

  case class SourceRef(typ: String, parameters: List[Parameter])

}
