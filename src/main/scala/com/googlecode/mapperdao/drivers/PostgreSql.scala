package com.googlecode.mapperdao.drivers

import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao._
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder
import com.googlecode.mapperdao.jdbc.Batch
import org.joda.time.{Duration, Period}
import org.postgresql.util.PGInterval

/**
 * @author kostantinos.kougios
 *
 *         14 Jul 2011
 */
class PostgreSql(val jdbc: Jdbc, val typeRegistry: TypeRegistry, val typeManager: TypeManager) extends Driver
{

	def batchStrategy(autogenerated: Boolean) = Batch.WithBatch

	val escapeNamesStrategy = new EscapeNamesStrategy
	{
		val invalidColumnNames = Set("end", "select", "where", "group")
		val invalidTableNames = Set("end", "select", "where", "group", "user")

		override def escapeColumnNames(name: String) = if (invalidColumnNames.contains(name.toLowerCase)) '"' + name + '"'; else name

		override def escapeTableNames(name: String) = if (invalidTableNames.contains(name.toLowerCase)) '"' + name + '"'; else name
	}
	val sqlBuilder = new SqlBuilder(this, escapeNamesStrategy)

	override protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = sequenceColumn match {
		case PK(_, columnName, true, sequence, _) => "NEXTVAL('%s')".format(sequence.get)
	}

	override def endOfQuery[ID, PC <: Persisted, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]) = {
		queryConfig.offset.foreach(o => q.appendSql("offset " + o))
		queryConfig.limit.foreach(l => q.appendSql("limit " + l))
		q
	}

	override def isDBKnownValue(tpe: Class[_]) = tpe == classOf[Period] || tpe == classOf[Duration]

	override def convertToDBKnownValue(tpe: Class[_], value: Any) = if (tpe == classOf[Period]) {
		def toPG(period: Period) = {
			val years = period.getYears
			val months = period.getMonths
			val days = period.getDays
			val hours = period.getHours
			val minutes = period.getMinutes
			val seconds = period.getSeconds
			new PGInterval(years, months, days, hours, minutes, seconds.toDouble)
		}
		// support for interval columns
		value match {
			case null => null
			case p: Period => toPG(p)
			case d: Duration => toPG(d.toPeriod)
		}
	} else value

	override def convertToScalaKnownValue(tpe: Class[_], value: Any) = value match {
		case null => null
		case i: PGInterval =>
			val p = new Period(i.getYears, i.getMonths, 0, i.getDays, i.getHours, i.getMinutes, i.getSeconds.toInt, 0)
			if (tpe == classOf[Period]) p
			else if (tpe == classOf[Duration]) p.toStandardDuration
			else throw new IllegalStateException("Unknown PGInterval type " + tpe)
		case _ => throw new IllegalStateException(tpe + " not supported by this driver")
	}

	override def toString = "PostgreSql"
}