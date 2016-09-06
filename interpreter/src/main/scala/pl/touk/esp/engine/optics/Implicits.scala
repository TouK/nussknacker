package pl.touk.esp.engine.optics

import pl.touk.esp.engine.canonicalgraph.CanonicalProcess

import scala.language.implicitConversions

object Implicits {

  implicit def toOptics(canonical: CanonicalProcess): ProcessOptics =
    new ProcessOptics(canonical)

}
