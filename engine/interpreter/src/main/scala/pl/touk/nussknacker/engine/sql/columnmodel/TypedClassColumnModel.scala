package pl.touk.nussknacker.engine.sql.columnmodel

import java.lang.reflect.Member

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ClassMemberPredicate}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel.ClazzToSqlType
import pl.touk.nussknacker.engine.sql.{Column, ColumnModel}
import pl.touk.nussknacker.engine.types.EspTypeUtils

private[columnmodel] object TypedClassColumnModel {
  def create(typed: Typed): ColumnModel = {
    val claz = typed.possibleTypes.head.klass
    val definition = EspTypeUtils.clazzDefinition(claz)(classExtractionSettings(claz))
    getColumns(definition)
  }

  private def classExtractionSettings(claz: Class[_]) = ClassExtractionSettings(Seq(new CreateColumnClassExtractionPredicate(claz)))

  private def getColumns(clazzDefinition: ClazzDefinition): ColumnModel = {
    val columns = for {
      (name, method) <- clazzDefinition.methods
      typ <- ClazzToSqlType.convert(name, method.refClazz)
    } yield Column(name, typ)
    ColumnModel(columns.toList)
  }

  class CreateColumnClassExtractionPredicate(claz: Class[_]) extends ClassMemberPredicate {
    private val declaredFieldsNames = claz.getDeclaredFields.toList.map(_.getName)

    override def matches(member: Member): Boolean = {
      !declaredFieldsNames.contains(member.getName)
    }
  }

}
