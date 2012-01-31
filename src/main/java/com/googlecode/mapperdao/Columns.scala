package com.googlecode.mapperdao

/**
 * Columns
 */

protected abstract class ColumnBase {
	def columnName: String
	def alias: String

	def isAutoGenerated: Boolean
	def isSequence: Boolean
}

abstract class SimpleColumn extends ColumnBase

case class Column(name: String) extends SimpleColumn {
	def columnName = name
	def alias = name
	def isAutoGenerated = false
	def isSequence = false
}

case class AutoGenerated(name: String, sequence: Option[String]) extends SimpleColumn {
	def columnName = name
	def alias = name
	def isAutoGenerated = true
	def isSequence = sequence.isDefined
}

case class PK(column: SimpleColumn) extends SimpleColumn {
	def columnName = column.columnName
	def alias = columnName
	def isAutoGenerated = column.isAutoGenerated
	def isSequence = column.isSequence
}

protected abstract class ColumnRelationshipBase[FPC, F](foreign: TypeRef[FPC, F]) extends ColumnBase

case class OneToOne[FPC, F](foreign: TypeRef[FPC, F], selfColumns: List[Column]) extends ColumnRelationshipBase(foreign) {
	def columnName = throw new IllegalStateException("OneToOne doesn't have a columnName")
	def alias = foreign.alias
	def isAutoGenerated = false
	def isSequence = false
}

case class OneToOneReverse[FPC, F](foreign: TypeRef[FPC, F], foreignColumns: List[Column]) extends ColumnRelationshipBase(foreign) {
	def columnName = throw new IllegalStateException("OneToOneReverse doesn't have a columnName")
	def alias = foreign.alias
	def isAutoGenerated = false
	def isSequence = false
}

case class OneToMany[FPC, F](foreign: TypeRef[FPC, F], foreignColumns: List[Column]) extends ColumnRelationshipBase(foreign) {
	def columnName = throw new IllegalStateException("OneToMany doesn't have a columnName")
	def alias = foreign.alias
	def isAutoGenerated = false
	def isSequence = false
}

case class ManyToOne[FPC, F](columns: List[Column], foreign: TypeRef[FPC, F]) extends ColumnRelationshipBase(foreign) {
	def columnName = throw new IllegalStateException("ManyToOne doesn't have a columnName")
	def alias = foreign.alias
	def isAutoGenerated = false
	def isSequence = false
}

case class ManyToMany[FPC, F](linkTable: LinkTable, foreign: TypeRef[FPC, F]) extends ColumnRelationshipBase(foreign) {
	def columnName = throw new IllegalStateException("ManyToMany doesn't have a columnName")
	def alias = foreign.alias
	def isAutoGenerated = false
	def isSequence = false
}

case class Type[PC, T](val clz: Class[T], val constructor: ValuesMap => T with PC with Persisted, table: Table[PC, T])

case class TypeRef[FPC, F](alias: String, entity: Entity[FPC, F])
