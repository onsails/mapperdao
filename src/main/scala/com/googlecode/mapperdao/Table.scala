package com.googlecode.mapperdao

/**
 * mapping tables to entities
 *
 * this is internal mapperdao API.
 *
 * @author kostantinos.kougios
 *
 * 12 Jul 2011
 */

case class Table[ID, PC <: DeclaredIds[ID], T](
		name: String,
		columnInfosPlain: List[ColumnInfoBase[T, _]],
		extraColumnInfosPersisted: List[ColumnInfoBase[T with PC, _]],
		val unusedPKColumnInfos: List[ColumnInfoBase[Any, Any]]) {

	val columns: List[ColumnBase] = extraColumnInfosPersisted.map(_.column) ::: columnInfosPlain.map(_.column)
	// the primary keys for this table
	val primaryKeys: List[PK] = columns.collect {
		case pk: PK => pk
	}
	val unusedPKs = unusedPKColumnInfos.map {
		case ci: ColumnInfo[Any, Any] => List(ci.column)
		case ci: ColumnInfoManyToOne[Any, Any, Any, Any] => ci.column.columns
		case ci: ColumnInfoTraversableOneToMany[Any, Any, Any, Any] => ci.column.columns
		case ci: ColumnInfoOneToOne[Any, Any, Any, Any] => ci.column.columns
	}.flatten

	val primaryKeysAndUnusedKeys = primaryKeys ::: unusedPKs
	val primaryKeysSize = primaryKeysAndUnusedKeys.size

	val primaryKeyColumnInfosForT = columnInfosPlain.collect {
		case ci @ ColumnInfo(_: PK, _, _) => ci
	}

	val primaryKeyColumnInfosForTWithPC = extraColumnInfosPersisted.collect {
		case ci @ ColumnInfo(_: PK, _, _) => ci
	}

	val primaryKeysAsColumns = primaryKeys.map(k => Column(k.name, k.tpe)).toSet

	val primaryKeysAsCommaSeparatedList = primaryKeys.map(_.name).mkString(",")

	val simpleTypeColumns = columns.collect {
		case c: Column => c
		case pk: PK => pk
	}
	val relationshipColumns = columns.collect {
		case c: ColumnRelationshipBase[_, _, _] => c
	}
	val autoGeneratedColumns = simpleTypeColumns.filter(_.isAutoGenerated)
	val columnsWithoutAutoGenerated = simpleTypeColumns.filterNot(_.isAutoGenerated) ::: relationshipColumns

	val simpleTypeSequenceColumns = simpleTypeColumns.filter(_.isSequence)
	val simpleTypeAutoGeneratedColumns = simpleTypeColumns.filter(_.isAutoGenerated)
	val simpleTypeNotAutoGeneratedColumns = simpleTypeColumns.filterNot(_.isAutoGenerated)

	val simpleTypeColumnInfos = columnInfosPlain.collect {
		case ci: ColumnInfo[T, _] => ci
	}

	val relationshipColumnInfos = columnInfosPlain.collect {
		case ci: ColumnInfoRelationshipBase[T, _, _, _, _] => ci
	}

	val oneToOneColumns: List[OneToOne[Any, Any, Any]] = columns.collect {
		case c: OneToOne[Any, Any, Any] => c
	}

	val oneToOneReverseColumns: List[OneToOneReverse[Any, Any, Any]] = columns.collect {
		case c: OneToOneReverse[Any, Any, Any] => c
	}

	val oneToManyColumns: List[OneToMany[Any, Any, Any]] = columns.collect {
		case c: OneToMany[Any, Any, Any] => c
	}
	val manyToOneColumns: List[ManyToOne[_, _, _]] = columns.collect {
		case mto: ManyToOne[_, _, _] => mto
	}
	val manyToOneColumnsFlattened: List[Column] = columns.collect {
		case ManyToOne(columns: List[Column], _) => columns
	}.flatten

	val manyToManyColumns: List[ManyToMany[Any, Any, Any]] = columns.collect {
		case c: ManyToMany[Any, Any, Any] => c
	}

	val oneToOneColumnInfos: List[ColumnInfoOneToOne[T, _, DeclaredIds[Any], _]] = columnInfosPlain.collect {
		case c: ColumnInfoOneToOne[T, _, DeclaredIds[Any], _] => c
	}
	val oneToOneReverseColumnInfos: List[ColumnInfoOneToOneReverse[T, _, DeclaredIds[Any], _]] = columnInfosPlain.collect {
		case c: ColumnInfoOneToOneReverse[T, _, DeclaredIds[Any], _] => c
	}

	val oneToManyColumnInfos: List[ColumnInfoTraversableOneToMany[T, Any, DeclaredIds[Any], _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableOneToMany[T, Any, DeclaredIds[Any], _] => c
	}
	val manyToOneColumnInfos: List[ColumnInfoManyToOne[T, Any, DeclaredIds[Any], _]] = columnInfosPlain.collect {
		case c: ColumnInfoManyToOne[T, Any, DeclaredIds[Any], _] => c
	}
	val manyToManyColumnInfos: List[ColumnInfoTraversableManyToMany[T, Any, DeclaredIds[Any], _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableManyToMany[T, Any, DeclaredIds[Any], _] => c
	}

	val columnToColumnInfoMap: Map[ColumnBase, ColumnInfoBase[T, _]] = columnInfosPlain.map(ci => (ci.column, ci)).toMap
	val pcColumnToColumnInfoMap: Map[ColumnBase, ColumnInfoBase[T with PC, _]] = extraColumnInfosPersisted.map(ci => (ci.column, ci)).toMap

	val manyToManyToColumnInfoMap: Map[ColumnBase, ColumnInfoTraversableManyToMany[T, _, _, _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableManyToMany[T, _, _, _] => (c.column, c)
	}.toMap

	val oneToManyToColumnInfoMap: Map[ColumnBase, ColumnInfoTraversableOneToMany[T, _, DeclaredIds[Any], _]] = columnInfosPlain.collect {
		case c: ColumnInfoTraversableOneToMany[T, _, DeclaredIds[Any], _] => (c.column, c)
	}.toMap

	def toListOfPrimaryKeyValues(o: T): List[Any] = toListOfPrimaryKeyAndValueTuples(o).map(_._2)
	def toListOfPrimaryKeyAndValueTuples(o: T): List[(PK, Any)] = toListOfColumnAndValueTuples(primaryKeys, o)
	def toListOfPrimaryKeySimpleColumnAndValueTuples(o: T): List[(SimpleColumn, Any)] = toListOfColumnAndValueTuples(primaryKeys, o)

	def toListOfUnusedPrimaryKeySimpleColumnAndValueTuples(o: Any): List[(SimpleColumn, Any)] =
		unusedPKColumnInfos.map { ci =>
			ci match {
				case ci: ColumnInfo[Any, Any] =>
					List((ci.column, ci.columnToValue(o)))
				case ci: ColumnInfoManyToOne[Any, Any, Any, Any] =>
					val l = ci.columnToValue(o)
					val fe = ci.column.foreign.entity
					val pks = fe.tpe.table.toListOfPrimaryKeyValues(l)
					ci.column.columns zip pks
				case ci: ColumnInfoTraversableOneToMany[Any, Any, Any, Any] =>
					o match {
						case p: Persisted =>
							ci.column.columns map { c =>
								(c, p.mapperDaoValuesMap.columnValue[Any](c))
							}
						case _ => Nil
					}
				case ci: ColumnInfoOneToOne[Any, Any, Any, Any] =>
					val l = ci.columnToValue(o)
					val fe = ci.column.foreign.entity
					val pks = fe.tpe.table.toListOfPrimaryKeyValues(l)
					ci.column.columns zip pks

				case ci: ColumnInfoRelationshipBase[Any, Any, Any, Any, Any] => Nil
			}
		}.flatten

	def toListOfColumnAndValueTuples[CB <: ColumnBase](columns: List[CB], o: T): List[(CB, Any)] = columns.map { c =>
		val ctco = columnToColumnInfoMap.get(c)
		if (ctco.isDefined) {
			if (o == null) (c, null) else (c, ctco.get.columnToValue(o))
		} else {
			o match {
				case pc: T with PC =>
					val ci = pcColumnToColumnInfoMap(c)
					(c, ci.columnToValue(pc))
				case null => (c, null)
			}
		}
	}

	def toColumnAndValueMap(columns: List[ColumnBase], o: T): Map[ColumnBase, Any] = columns.map { c => (c, columnToColumnInfoMap(c).columnToValue(o)) }.toMap
	def toPCColumnAndValueMap(columns: List[ColumnBase], o: T with PC): Map[ColumnBase, Any] = columns.map { c => (c, pcColumnToColumnInfoMap(c).columnToValue(o)) }.toMap

	def toColumnAliasAndValueMap(columns: List[ColumnBase], o: T): Map[String, Any] = toColumnAndValueMap(columns, o).map(e => (e._1.alias, e._2))
	def toPCColumnAliasAndValueMap(columns: List[ColumnBase], o: T with PC): Map[String, Any] = toPCColumnAndValueMap(columns, o).map(e => (e._1.alias, e._2))
}

case class LinkTable(name: String, left: List[Column], right: List[Column])
