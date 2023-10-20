package pl.touk.nussknacker.engine.api

package object typed {

  /**
    * deprecated - introduced to easily remove TypeMap class
    * use explicitly java.util.Map[String, Any] instead
    */
  type TypedMap = java.util.Map[String, Any]

  object TypedMap {

    import scala.jdk.CollectionConverters._

    def apply(scalaFields: Map[String, Any]): TypedMap = new java.util.HashMap[String, Any](scalaFields.asJava)

    def apply(): TypedMap = new java.util.HashMap[String, Any]()
  }

}
