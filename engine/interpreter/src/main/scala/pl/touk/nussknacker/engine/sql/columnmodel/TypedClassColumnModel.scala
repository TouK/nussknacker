package pl.touk.nussknacker.engine.sql.columnmodel

import java.lang.reflect.Member

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ClassMemberPredicate, PropertyFromGetterExtractionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel.ClazzToSqlType
import pl.touk.nussknacker.engine.sql.{Column, ColumnModel}
import pl.touk.nussknacker.engine.types.EspTypeUtils

private[columnmodel] object TypedClassColumnModel {
  def create(typed: TypedClass): ColumnModel = {
    val claz = typed.klass
    val definition = EspTypeUtils.clazzDefinition(claz)(classExtractionSettings(claz))
    getColumns(definition)
  }

  private def classExtractionSettings(claz: Class[_]) = ClassExtractionSettings(Seq.empty, Seq(new CreateColumnClassExtractionPredicate(claz)),
    Seq.empty, PropertyFromGetterExtractionStrategy.AddPropertyNextToGetter)

  private def getColumns(clazzDefinition: ClazzDefinition): ColumnModel = {
    val columns = for {
      (name, methods) <- clazzDefinition.methods
      typ <- ClazzToSqlType.convert(name, Typed(methods.map(_.refClazz).toSet), clazzDefinition.clazzName.display)
    } yield Column(name, typ)
    ColumnModel(columns.toList)
  }

  class CreateColumnClassExtractionPredicate(clazzDeclaringFields: Class[_]) extends ClassMemberPredicate {
    private val declaredFieldsNames = clazzDeclaringFields.getDeclaredFields.toList.map(_.getName)

    override def matchesClass(clazz: Class[_]): Boolean = true

    override def matchesMember(member: Member): Boolean = {
      !declaredFieldsNames.contains(member.getName)
    }
  }

}
