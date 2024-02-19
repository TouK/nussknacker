package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.definition.Parameter

// The purpose of this trait is to give ability to return static parameters next to existing dynamic once.
// Static parameters are mainly used to define initial set of parameters that will be used when
// component will be placed on diagram
trait WithStaticParameters { self: DynamicComponent[_] =>

  def staticParameters: List[Parameter]

}
