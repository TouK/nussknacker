package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, LazyParameter, MethodToInvoke, ParamName}

// In custom stream by default all parameters are eagerly evaluated, you need to define type LazyParameter to make it lazy
class SimpleTypesCustomStreamTransformer extends CustomStreamTransformer with Serializable {
  @MethodToInvoke(returnType = classOf[Void])
  def invoke(@ParamName("booleanParam") booleanParam: java.lang.Boolean,
             @ParamName("lazyBooleanParam") lazyBooleanParam: LazyParameter[java.lang.Boolean],
             @ParamName("stringParam") string: String,
             @ParamName("intParam") intParam: Int,
             @ParamName("bigDecimalParam") bigDecimalParam: java.math.BigDecimal,
             @ParamName("bigIntegerParam") bigIntegerParam: java.math.BigInteger): Unit = {
  }
}
