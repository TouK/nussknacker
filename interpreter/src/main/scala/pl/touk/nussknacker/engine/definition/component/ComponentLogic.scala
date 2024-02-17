package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.Params

// Purpose of this class is to allow to invoke Component's logic. It is encapsulated to the separated class to make
// stubbing and other post-processing easier. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
trait ComponentLogic extends Serializable {

  def run(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any

  final def transformResult(f: Any => Any): ComponentLogic = new ComponentLogic {

    override def run(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
      val originalResult = ComponentLogic.this.run(params, outputVariableNameOpt, additional)
      f(originalResult)
    }

  }

}

object ComponentLogic {

  val nullReturningComponentLogic: ComponentLogic = new ComponentLogic {
    override def run(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = null
  }

}
