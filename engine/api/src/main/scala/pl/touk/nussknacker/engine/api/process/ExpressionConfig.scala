package pl.touk.nussknacker.engine.api.process

//TODO: how to make this config less spel-centric?
case class ExpressionConfig(globalProcessVariables: Map[String, WithCategories[AnyRef]],
                            globalImports: List[WithCategories[String]],
                            optimizeCompilation: Boolean = true)
