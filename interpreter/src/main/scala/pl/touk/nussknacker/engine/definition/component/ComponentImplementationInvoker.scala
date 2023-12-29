package pl.touk.nussknacker.engine.definition.component

// Purpose of this class is to allow to invoke Component's implementation. It is encapsulated to the separated class to make
// stubbing and other post-processing easier. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
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
