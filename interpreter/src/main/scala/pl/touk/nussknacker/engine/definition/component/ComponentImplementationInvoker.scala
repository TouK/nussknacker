package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.Params

// Purpose of this class is to allow to invoke Component's logic. It is encapsulated to the separated class to make
// stubbing and other post-processing easier. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
trait ComponentImplementationInvoker extends Serializable {

  def invokeMethod(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any

  final def transformResult(f: Any => Any): ComponentImplementationInvoker = new ComponentImplementationInvoker {

    override def invokeMethod(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
      val originalResult = ComponentImplementationInvoker.this.invokeMethod(params, outputVariableNameOpt, additional)
      f(originalResult)
    }

  }

}

object ComponentImplementationInvoker {

  val nullReturningComponentImplementationInvoker: ComponentImplementationInvoker = new ComponentImplementationInvoker {
    override def invokeMethod(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any =
      null
  }

}
