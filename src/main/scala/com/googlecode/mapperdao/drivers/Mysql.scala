package com.googlecode.mapperdao.drivers

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder
import com.googlecode.mapperdao.jdbc.Batch
import com.googlecode.mapperdao.schema.{SimpleColumn, ColumnBase}

/**
 * @author kostantinos.kougios
 *
 *         2 Sep 2011
 */
class Mysql(override val jdbc: Jdbc, val typeRegistry: TypeRegistry, val typeManager: TypeManager) extends Driver
{

	def batchStrategy(autogenerated: Boolean) = Batch.WithBatch

	val escapeNamesStrategy = new EscapeNamesStrategy
	{
		val invalidColumnNames = Set("int", "long", "float", "double")

		override def escapeColumnNames(name: String) = if (invalidColumnNames.contains(name)) "`" + name + "`" else name

		override def escapeTableNames(name: String) = name
	}
	val sqlBuilder = new SqlBuilder(this, escapeNamesStrategy)

	override protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = throw new IllegalStateException("MySql doesn't support sequences")

	protected[mapperdao] override def getAutoGenerated(
		m: java.util.Map[String, Object],
		column: SimpleColumn
		) = m.get("GENERATED_KEY")

	override def endOfQuery[ID, PC <: Persisted, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]) = {
		if (queryConfig.offset.isDefined || queryConfig.limit.isDefined) {
			val offset = queryConfig.offset.getOrElse(0)
			val limit = queryConfig.limit.getOrElse(Long.MaxValue)
			q.appendSql("LIMIT " + offset + "," + limit)
		}
		q
	}

	override def toString = "MySql"
}