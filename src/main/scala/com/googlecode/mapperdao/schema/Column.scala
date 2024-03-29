package com.googlecode.mapperdao.schema

import com.googlecode.mapperdao.EntityBase

case class Column(entity: EntityBase[_, _], name: String, tpe: Class[_]) extends SimpleColumn
{
	def alias = name

	def isAutoGenerated = false

	def isSequence = false

	override def equals(v: Any) = v match {
		case c: SimpleColumn => c.name == name
		case _ => false
	}

	override def hashCode = name.hashCode
}
