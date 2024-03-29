package com.googlecode.mapperdao.schema

import com.googlecode.mapperdao.EntityBase

case class PK(
	entity: EntityBase[_, _],
	name: String,
	isAutoGenerated: Boolean,
	sequence: Option[String],
	tpe: Class[_]
	) extends SimpleColumn
{
	def alias = name

	def isSequence = sequence.isDefined

	override def equals(v: Any) = v match {
		case c: SimpleColumn => c.name == name
		case _ => false
	}

	override def hashCode = name.hashCode
}
