package pl.touk.nussknacker.engine.api

package object typed {

  type TypedMap = java.util.HashMap[String, Any]

  object TypedMap {

    import scala.jdk.CollectionConverters._

    def apply(scalaFields: Map[String, Any]): TypedMap = new TypedMap(scalaFields.asJava)
  }

}
