package pl.touk.nussknacker.engine.spel.ast

import org.scalatest.{FunSpec, Matchers}
import org.springframework.expression.spel.ast.PropertyOrFieldReference

class SpelNodeTransformerSpec extends FunSpec with Matchers {

  val parser = new org.springframework.expression.spel.standard.SpelExpressionParser

  it("should transform nodes") {
    val ast = parser.parseRaw("#foo.bar").getAST
    val transformer = new SpelNodeTransformer({
      case e: PropertyOrFieldReference if e.getName == "bar" =>
        args =>
          new PropertyOrFieldReference(e.isNullSafe, "baz", args.position)
    })
    val transformed = transformer.transform(ast)
    val printed = SpelNodePrettyPrinter.pretty(transformed)
    printed shouldEqual "#foo.baz"
  }

}
