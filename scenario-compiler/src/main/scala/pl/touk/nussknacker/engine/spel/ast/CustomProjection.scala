package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.TypedValue
import org.springframework.expression.spel.ExpressionState
import org.springframework.expression.spel.ast.{Projection, SpelNodeImpl, ValueRef}

class CustomProjection(private val projection: Projection) extends SpelNodeImpl(projection.getStartPosition, projection.getEndPosition, projection.getChild(0).asInstanceOf[SpelNodeImpl]) {

  override def getValueInternal(expressionState: ExpressionState): TypedValue = projection.getValueInternal(expressionState)

  override def getValueRef(state: ExpressionState): ValueRef = {
    val method = classOf[Projection].getDeclaredMethod("getValueRef", classOf[ExpressionState])
    method.invoke(projection, state).asInstanceOf[ValueRef]
  }

  override def toStringAST: String = projection.toStringAST
}
