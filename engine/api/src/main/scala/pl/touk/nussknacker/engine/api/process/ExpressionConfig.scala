package pl.touk.nussknacker.engine.api.process

case class ExpressionConfig(globalProcessVariables: Map[String, WithCategories[AnyRef]],
                            globalImports: List[WithCategories[String]])
