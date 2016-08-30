package pl.touk.esp.engine.api

sealed trait PartReference

case class NextPartReference(id: String) extends PartReference
case object DeadEndReference extends PartReference
case object EndReference extends PartReference
