package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, LazyParameter, MethodToInvoke, ParamName}

case object NoneReturnTypeTransformer extends CustomStreamTransformer {
  @MethodToInvoke(returnType = classOf[Void])
  def execute(@ParamName("expression") expression: LazyParameter[java.lang.Boolean]): Unit = {}
}
