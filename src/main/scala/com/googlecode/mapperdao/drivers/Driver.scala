package com.googlecode.mapperdao.drivers

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.jdbc.JdbcMap
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder
import com.googlecode.mapperdao.jdbc.Batch
import com.googlecode.mapperdao.schema._
import com.googlecode.mapperdao.jdbc.UpdateResult
import com.googlecode.mapperdao.schema.ManyToMany
import com.googlecode.mapperdao.schema.OneToOneReverse

/**
 * all database drivers must implement this trait
 *
 * @author kostantinos.kougios
 *
 *         14 Jul 2011
 */
abstract class Driver
{
	val jdbc: Jdbc
	val typeRegistry: TypeRegistry
	val typeManager: TypeManager

	def batchStrategy(autogenerated: Boolean): Batch.Strategy

	/**
	 * =====================================================================================
	 * utility methods
	 * =====================================================================================
	 */
	val escapeNamesStrategy: EscapeNamesStrategy
	val sqlBuilder: SqlBuilder

	protected[mapperdao] def getAutoGenerated(
		m: java.util.Map[String, Object],
		column: SimpleColumn
		): Any = m.get(column.name)

	/**
	 * =====================================================================================
	 * INSERT
	 * =====================================================================================
	 */

	protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = throw new IllegalStateException("Please implement")

	/**
	 * default impl of the insert statement generation
	 */
	def insertSql[ID, T](
		tpe: Type[ID, T],
		args: List[(SimpleColumn, Any)]
		) = {
		val s = new sqlBuilder.InsertBuilder
		s.into(tpe.table.schemaName, tpe.table.name)

		val sequenceColumns = tpe.table.simpleTypeSequenceColumns
		if (!args.isEmpty || !sequenceColumns.isEmpty) {
			val seqVal = sequenceColumns zip sequenceColumns.map {
				sequenceSelectNextSql _
			}
			s.columnAndSequences(seqVal)
			s.columnAndValues(args)
		}
		s
	}

	def insertManyToManySql(
		manyToMany: ManyToMany[_, _],
		left: List[Any],
		right: List[Any]
		) = {
		val values = left ::: right
		val s = new sqlBuilder.InsertBuilder
		val linkTable = manyToMany.linkTable
		s.into(linkTable.schemaName, linkTable.name)
		val cav = (linkTable.left ::: linkTable.right) zip values
		s.columnAndValues(cav)
		s
	}

	/**
	 * =====================================================================================
	 * UPDATE
	 * =====================================================================================
	 */
	/**
	 * default impl of the insert statement generation
	 */
	def updateSql[ID, T](
		tpe: Type[ID, T],
		args: List[(SimpleColumn, Any)],
		pkArgs: List[(SimpleColumn, Any)]
		) = {
		val s = new sqlBuilder.UpdateBuilder
		s.table(tpe.table.schemaName, tpe.table.name)
		s.set(args)
		s.where(pkArgs, "=")
		s
	}

	/**
	 * links one-to-many objects to their parent
	 */
	def doUpdateOneToManyRef[ID, T](tpe: Type[ID, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult = {
		val r = updateOneToManyRefSql(tpe, foreignKeys, pkArgs).result
		jdbc.update(r.sql, r.values)
	}

	protected def updateOneToManyRefSql[ID, T](tpe: Type[ID, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]) = {
		val s = new sqlBuilder.UpdateBuilder
		s.table(tpe.table.schemaName, tpe.table.name)
		s.set(foreignKeys)
		s.where(pkArgs, "=")
		s
	}

	/**
	 * delete many-to-many rows from link table
	 */
	def deleteManyToManySql(
		manyToMany: ManyToMany[_, _],
		leftKeys: List[(SimpleColumn, Any)],
		rightKeys: List[(SimpleColumn, Any)]
		) = {
		val linkTable = manyToMany.linkTable
		val s = new sqlBuilder.DeleteBuilder
		s.from(linkTable.schemaName, linkTable.name)
		val cav = leftKeys ::: rightKeys
		s.where(cav, "=")
		s
	}

	def doDeleteAllManyToManyRef[ID, T](tpe: Type[ID, T], manyToMany: ManyToMany[_, _], fkKeyValues: List[Any]): UpdateResult = {
		val r = deleteAllManyToManyRef(tpe, manyToMany, fkKeyValues).result
		jdbc.update(r.sql, r.values)
	}

	protected def deleteAllManyToManyRef[ID, T](tpe: Type[ID, T], manyToMany: ManyToMany[_, _], fkKeyValues: List[Any]) = {
		val s = new sqlBuilder.DeleteBuilder
		s.from(manyToMany.linkTable.schemaName, manyToMany.linkTable.name)
		s.where(manyToMany.linkTable.left zip fkKeyValues, "=")
		s
	}

	/**
	 * =====================================================================================
	 * SELECT
	 * =====================================================================================
	 */

	/**
	 * default impl of select
	 */
	def doSelect[ID, T](selectConfig: SelectConfig, tpe: Type[ID, T], where: List[(SimpleColumn, Any)]): List[DatabaseValues] = {
		val result = selectSql(selectConfig, tpe, where).result

		// 1st step is to get the simple values
		// of this object from the database
		jdbc.queryForList(result.sql, result.values).map {
			j =>
				typeManager.correctTypes(this, tpe, j)
		}
	}

	protected def selectSql[ID, T](selectConfig: SelectConfig, tpe: Type[ID, T], where: List[(SimpleColumn, Any)]) = {
		val sql = new sqlBuilder.SqlSelectBuilder
		sql.columns(null,
			tpe.table.distinctSelectColumnsForSelect
		)
		sql.from(tpe.table.schemaName, tpe.table.name, null, applyHints(selectConfig.hints))
		sql.where(null, where, "=")
		sql
	}

	private def applyHints(hints: SelectHints) = {
		val h = hints.afterTableName
		if (!h.isEmpty) {
			" " + h.map {
				_.hint
			}.mkString(" ") + " "
		} else ""
	}

	def doSelectManyToMany[ID, T, FID, F](selectConfig: SelectConfig, tpe: Type[ID, T], ftpe: Type[FID, F], manyToMany: ManyToMany[FID, F], leftKeyValues: List[(SimpleColumn, Any)]): List[DatabaseValues] = {
		val r = selectManyToManySql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
		jdbc.queryForList(r.sql, r.values).map(j => typeManager.correctTypes(this, ftpe, j))
	}

	protected def selectManyToManySql[ID, T, FID, F](selectConfig: SelectConfig, tpe: Type[ID, T], ftpe: Type[FID, F], manyToMany: ManyToMany[FID, F], leftKeyValues: List[(SimpleColumn, Any)]) = {
		val ftable = ftpe.table
		val linkTable = manyToMany.linkTable

		val sql = new sqlBuilder.SqlSelectBuilder
		val fColumns = ftpe.table.selectColumns
		sql.columns("f", fColumns)
		sql.from(ftpe.table.schemaName, ftpe.table.name, "f", applyHints(selectConfig.hints))
		val j = sql.innerJoin(linkTable.schemaName, linkTable.name, "l", applyHints(selectConfig.hints))
		ftable.primaryKeys.zip(linkTable.right).foreach {
			case (left, right) =>
				j.and("f", left.name, "=", "l", right.name)
		}
		sql.where("l", leftKeyValues, "=")
		sql
	}

	def doSelectManyToManyCustomLoader[ID, T, FID, F](selectConfig: SelectConfig, tpe: Type[ID, T], ftpe: Type[FID, F], manyToMany: ManyToMany[FID, F], leftKeyValues: List[(SimpleColumn, Any)]): List[JdbcMap] = {
		val r = selectManyToManyCustomLoaderSql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
		jdbc.queryForList(r.sql, r.values)
	}

	protected def selectManyToManyCustomLoaderSql[ID, T, FID, F](selectConfig: SelectConfig, tpe: Type[ID, T], ftpe: Type[FID, F], manyToMany: ManyToMany[FID, F], leftKeyValues: List[(SimpleColumn, Any)]) = {
		val linkTable = manyToMany.linkTable
		val sql = new sqlBuilder.SqlSelectBuilder
		sql.columns(null, linkTable.right)
		sql.from(linkTable.schemaName, linkTable.name, null, applyHints(selectConfig.hints))
		sql.where(null, leftKeyValues, "=")
		sql

	}

	/**
	 * selects all id's of external entities and returns them in a List[List[Any]]
	 */
	def doSelectManyToManyForExternalEntity[ID, T, FID, F](selectConfig: SelectConfig, tpe: Type[ID, T], ftpe: Type[FID, F], manyToMany: ManyToMany[FID, F], leftKeyValues: List[(SimpleColumn, Any)]): List[List[Any]] = {
		val r = selectManyToManySqlForExternalEntity(tpe, ftpe, manyToMany, leftKeyValues).result
		val l = jdbc.queryForList(r.sql, r.values)

		val linkTable = manyToMany.linkTable
		val columns = linkTable.right.map(_.name)
		l.map {
			j =>
				columns.map(c => j(c))
		}
	}

	protected def selectManyToManySqlForExternalEntity[ID, T, FID, F](tpe: Type[ID, T], ftpe: Type[FID, F], manyToMany: ManyToMany[FID, F], leftKeyValues: List[(SimpleColumn, Any)]) = {
		val linkTable = manyToMany.linkTable

		val sql = new sqlBuilder.SqlSelectBuilder
		sql.columns(null, linkTable.right)
		sql.from(linkTable.schemaName, linkTable.name)
		sql.where(null, leftKeyValues, "=")
		sql
	}

	/**
	 * =====================================================================================
	 * DELETE
	 * =====================================================================================
	 */
	def doDelete[ID, T](tpe: Type[ID, T], whereColumnValues: List[(SimpleColumn, Any)]) {
		val s = deleteSql(tpe, whereColumnValues).result
		jdbc.update(s.sql, s.values)
	}

	def deleteSql[ID, T](tpe: Type[ID, T], whereColumnValues: List[(SimpleColumn, Any)]) = {
		val s = new sqlBuilder.DeleteBuilder
		s.from(sqlBuilder.Table(tpe.table.schemaName, tpe.table.name))
		s.where(whereColumnValues, "=")
		s
	}

	def doDeleteOneToOneReverse[ID, T, FID, FT](tpe: Type[ID, T], ftpe: Type[FID, FT], oneToOneReverse: OneToOneReverse[FID, FT], keyValues: List[Any]) {
		val r = deleteOneToOneReverseSql(tpe, ftpe, oneToOneReverse.foreignColumns zip keyValues).result
		jdbc.update(r.sql, r.values)
	}

	def deleteOneToOneReverseSql[ID, T, FID, FT](tpe: Type[ID, T], ftpe: Type[FID, FT], columnAndValues: List[(SimpleColumn, Any)]) = {
		val s = new sqlBuilder.DeleteBuilder
		s.from(sqlBuilder.Table(ftpe.table.schemaName, ftpe.table.name))
		s.where(columnAndValues, "=")
		s
	}

	/**
	 * =====================================================================================
	 * QUERIES
	 * =====================================================================================
	 */

	// select ... from 
	def startQuery[ID, PC <: Persisted, T](
		q: sqlBuilder.SqlSelectBuilder,
		queryConfig: QueryConfig,
		aliases: QueryDao.Aliases,
		qe: Query.Builder[ID, PC, T], columns: List[SimpleColumn]
		) = {
		val entity = qe.entity
		val tpe = entity.tpe
		queryAfterSelect(q, queryConfig, aliases, qe, columns)
		val alias = aliases(entity)

		q.columns(alias, columns)
		val hints = queryConfig.hints.afterTableName.map(_.hint).mkString
		q.from(tpe.table.schemaName, tpe.table.name, alias, hints)
	}

	def queryAfterSelect[ID, PC <: Persisted, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[ID, PC, T], columns: List[SimpleColumn]) {}

	def shouldCreateOrderByClause(queryConfig: QueryConfig): Boolean = true

	// called at the start of each query sql generation, sql is empty at this point
	def beforeStartOfQuery[ID, PC <: Persisted, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T], columns: List[SimpleColumn]): sqlBuilder.SqlSelectBuilder = q

	// called at the end of each query sql generation
	def endOfQuery[ID, PC <: Persisted, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[ID, PC, T]): sqlBuilder.SqlSelectBuilder = q

	/**
	 * =====================================================================================
	 * generic queries
	 * =====================================================================================
	 */
	def queryForList[ID, T](queryConfig: QueryConfig, tpe: Type[ID, T], sql: String, args: List[Any]): List[DatabaseValues] =
		jdbc.queryForList(sql, args).map {
			j => typeManager.correctTypes(this, tpe, j)
		}

	def queryForLong(queryConfig: QueryConfig, sql: String, args: List[Any]): Long = jdbc.queryForLong(sql, args)

	/**
	 * =====================================================================================
	 * sql-function related methods
	 * =====================================================================================
	 */

	def functionCallPrependUser: Option[String] = None

	/**
	 * =====================================================================================
	 * db-specific data type related methods
	 * =====================================================================================
	 */

	/**
	 * return true only if tpe is known to the specific driver
	 */
	def isDBKnownValue(tpe: Class[_]) = false

	/**
	 * convert a db-known value from scala to the driver-specific type
	 */
	def convertToDBKnownValue(tpe: Class[_], value: Any) = value

	/**
	 * convert a scala-known value from the driver-specific type to the scala type
	 */
	def convertToScalaKnownValue(tpe: Class[_], value: Any): Any = throw new IllegalStateException(tpe + " not supported by this driver")

	/**
	 * =====================================================================================
	 * error handling
	 * =====================================================================================
	 */
	def expandError(e: Throwable): List[Throwable] = List(e)

	/**
	 * =====================================================================================
	 * standard methods
	 * =====================================================================================
	 */
	override def toString = "Driver(%s,%s)".format(jdbc, typeRegistry)
}
