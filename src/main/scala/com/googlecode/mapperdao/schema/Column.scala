package com.googlecode.mapperdao.schema

import com.googlecode.mapperdao.{Persisted, Entity}

case class Column(entity: Entity[_, _ <: Persisted, _], name: String, tpe: Class[_]) extends SimpleColumn
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