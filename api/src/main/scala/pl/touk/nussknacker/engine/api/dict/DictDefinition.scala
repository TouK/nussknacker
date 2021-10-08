package pl.touk.nussknacker.engine.api.dict

import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed}

/**
 * It defines dictionary which will contain values of type `valueType`. Value will be created
 * based on String `key`. `key` is presented as a `label` in UI.
 *
 * All definitions should be registered in `ProcessConfigCreator.expressionConfig().dictionaries`
 * After registration, definitions will be available for `DictRegistry` and other services using it (like `DictQueryService`)
 */
trait DictDefinition extends Serializable {

  def valueType(dictId: String): SingleTypingResult

  // It should return value in declared type.
  def value(key: String): Any

}

/**
 * If this instance wil be used in global variables, will be typed to TypedDict.
 */
// TODO: Should take only DictDefinition as a parameter to avoid dictId desynchronization - see notice in ExpressionConfig
case class DictInstance(dictId: String, definition: DictDefinition) {

  def valueType: SingleTypingResult = definition.valueType(dictId)

  def value(key: String): Any = definition.value(key)

}

/**
 * It is helper mixin when value is exact as key (without additional transformation)
 */
trait ReturningKeyWithoutTransformation { self: DictDefinition =>

  override def value(key: String): Any = key

  // TODO: Should not take dictId - see notice in ExpressionConfig
  override def valueType(dictId: String): SingleTypingResult = Typed.taggedDictValue(Typed.typedClass[String], dictId)

}
