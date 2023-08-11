package pl.touk.nussknacker.engine.api.definition

object ParameterImplicits {

  implicit class RichParameter(parameter: Parameter) {

    def extract[T](params: Map[String, Any]): T =
      params(parameter.name).asInstanceOf[T]

    def extractOptional[T](params: Map[String, Any]): Option[T] =
      Option(extract(params))

  }

}
