package pl.touk.nussknacker.engine.definition.component

trait ComponentImplementationInvoker extends Serializable {

  def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any

}

object ComponentImplementationInvoker {

  val nullImplementationInvoker: ComponentImplementationInvoker = new ComponentImplementationInvoker {

    override def invokeMethod(
        params: Map[String, Any],
        outputVariableNameOpt: Option[String],
        additional: Seq[AnyRef]
    ): Any = null

  }

}
