package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.state.persistcmds._
import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.state.persisted._
import com.googlecode.mapperdao.state.persistcmds.PersistCmd
import com.googlecode.mapperdao.state.persistcmds.InsertCmd
import exceptions.PersistException
import state.prioritise.Prioritized
import com.googlecode.mapperdao.schema.{SimpleColumn, ColumnInfo}
import com.googlecode.mapperdao.internal.{MutableIdentityHashMap, MutableIdentityHashSet}

/**
 * converts commands to database operations, executes
 * them and returns the resulting persisted allNodes.
 *
 * @author kostantinos.kougios
 *
 *         22 Nov 2012
 */
class CmdToDatabase(
	updateConfig: UpdateConfig,
	protected val driver: Driver,
	typeManager: TypeManager,
	prioritized: Prioritized
	)
{

	private val jdbc = driver.jdbc

	// keep track of which entities were already persisted in order to
	// know if a depended entity can be persisted
	private val persistedIdentities = new MutableIdentityHashSet[Any]

	val dependentMap = {
		val m = new MutableIdentityHashMap[Any, Set[ValuesMap]]

		prioritized.dependent.foreach {
			case DependsCmd(vm, dependsOnVm) =>
				val set = (m.get(vm.o) match {
					case None => Set.empty[ValuesMap]
					case Some(s) => s
				}) + dependsOnVm
				m(vm.o) = set
		}

		m
	}

	// true if all needed related entities are already persisted.
	private def allDependenciesAlreadyPersisted(vm: ValuesMap) = dependentMap.get(vm.o) match {
		case None => true
		case Some(set) =>
			set.forall(vm => persistedIdentities(vm.o))
	}

	private case class Node(
		sql: driver.sqlBuilder.Result,
		cmd: PersistCmd
		)

	def execute: List[PersistedNode[_, _]] = {

		// we need to flatten out the sql's so that we can batch process them
		// but also keep the tree structure so that we return only PersistedNode's
		// for the top level PersistedCmd's

		val cmdList = (prioritized.high ::: List(prioritized.low))

		/**
		 * cmdList contains a list of prioritized PersistCmd, according to their
		 * relevant entity priority. Some times related entities still are
		 * scheduled to be persisted before the entity that references them.
		 * i.e. a one-to-many Person(name,Set[Person])
		 *
		 * We need to make sure that all entities are persisted in the
		 * correct order.
		 */
		def persist(cmdList: List[List[PersistCmd]], depth: Int) {
			if (depth > 100) {
				throw new IllegalStateException("after 100 iterations, there are still unpersisted entities. Maybe a mapperdao bug. Entities remaining : " + cmdList)
			}
			val remaining = cmdList.map {
				cmds =>
					val (toProcess, remaining) = findToProcess(cmds)
					val nodes = toNodes(toProcess)
					db(nodes)
					remaining
			}.filterNot(_.isEmpty)
			if (!remaining.isEmpty) persist(remaining, depth + 1)
		}

		persist(cmdList, 0)
		persist(List(prioritized.lowest), 0)

		cmdList.map {
			cmds =>
			// now the batches were executed and we got a tree with
			// the commands and the autogenerated keys.
				toPersistedNodes(cmds)
		}.flatten
	}

	private def findToProcess(cmds: List[PersistCmd]) = cmds.partition {
		case c: CmdWithNewVM =>
			allDependenciesAlreadyPersisted(c.newVM)
		case _ => true
	}

	private def db(allNodes: List[Node]) {
		// group the sql's and batch-execute them
		allNodes.groupBy {
			_.sql.sql
		}.foreach {
			case (sql, nodes) =>
				val cmd = nodes.head.cmd
				val args = nodes.map {
					case Node(s, cmd) =>
						s.values.toArray
				}.toArray

				cmd match {
					case cmdWe: CmdWithType[_, _] =>
						val tpe = cmdWe.tpe
						val table = tpe.table
						val autoGeneratedColumnNames = cmd match {
							case InsertCmd(_, _, _, _) =>
								table.autoGeneratedColumnNamesArray
							case _ => Array[String]()
						}
						val bo = BatchOptions(driver.batchStrategy(autoGeneratedColumnNames.length > 0), autoGeneratedColumnNames)

						// do the batch update
						val br = try {
							jdbc.batchUpdate(bo, sql, args)
						} catch {
							case e: Throwable =>
								throw new PersistException("An error occured during a batch operation for " + sql, driver.expandError(e))
						}

						// now extract the keys and set them into the allNodes
						if (br.keys != null) {
							val keys: Array[List[(SimpleColumn, Any)]] = br.keys.map {
								m: java.util.Map[String, Object] =>
									table.autoGeneratedColumns.map {
										column =>
											(
												column,
												table.pcColumnToColumnInfoMap(column) match {
													case ci: ColumnInfo[_, _] =>
														val value = driver.getAutoGenerated(m, column)
														typeManager.toActualType(ci.dataType, value)
												}
												)
									}
							}
							// note down the generated keys
							(nodes zip keys) foreach {
								case (node, key) =>
									node.cmd match {
										case InsertCmd(_, newVM, _, _) =>
											newVM.addAutogeneratedKeys(key)
									}
							}
						}
					case InsertManyToManyCmd(_, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case DeleteManyToManyCmd(entity, foreignEntity, manyToMany, entityVM, foreignEntityVM) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case UpdateExternalManyToManyCmd(_, _, _, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
				}
		}
	}

	private def toPersistedNodes(nodes: List[PersistCmd]) = nodes.collect {
		case InsertCmd(tpe, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, None, newVM, mainEntity) :: Nil
		case UpdateCmd(tpe, oldVM, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, Some(oldVM), newVM, mainEntity) :: Nil
		case MockCmd(tpe, oldVM, newVM) =>
			EntityPersistedNode(tpe, Some(oldVM), newVM, false) :: Nil
		case UpdateExternalManyToManyCmd(tpe, newVM, foreignEntity, manyToMany, added, intersect, removed) =>
			val add = added.map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			}.toList
			val in = intersect.map {
				case (oldO, newO) =>
					ExternalEntityPersistedNode(foreignEntity, newO)
			}.toList
			val rem = removed.map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			}.toList
			add ::: in ::: rem ::: Nil
		case UpdateExternalManyToOneCmd(foreignEntity, ci, newVM, fo) =>
			ExternalEntityPersistedNode(foreignEntity, fo) :: Nil
		case InsertOneToManyExternalCmd(foreignEntity, oneToMany, entityVM, added) =>
			val ue = UpdateExternalOneToMany(updateConfig, entityVM, added, Nil, Nil)
			foreignEntity.oneToManyOnUpdateMap(oneToMany)(ue)
			added.map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			}
		case UpdateExternalOneToManyCmd(foreignEntity, oneToMany, entityVM, added, intersected, removed) =>
			val ue = UpdateExternalOneToMany(updateConfig, entityVM, added, intersected, removed)
			foreignEntity.oneToManyOnUpdateMap(oneToMany)(ue)
			(added ++ removed).map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			} ++ intersected.map {
				case (o, n) =>
					ExternalEntityPersistedNode(foreignEntity, n)
			}
		case InsertOneToOneReverseExternalCmd(tpe, foreignEntity, oneToOneReverse, entityVM, ft) =>
			val o = tpe.constructor(null, None, entityVM)
			val ue = InsertExternalOneToOneReverse[Any, Any](updateConfig, o, ft)
			val m = foreignEntity.oneToOneOnInsertMap(oneToOneReverse).asInstanceOf[foreignEntity.OnInsertOneToOneReverse[Any]]
			m(ue)
			ExternalEntityPersistedNode(foreignEntity, ft) :: Nil
		case UpdateExternalOneToOneReverseCmd(tpe, foreignEntity, oneToOneReverse, entityVM, oldFT, newFT) =>
			val o = tpe.constructor(null, None, entityVM)
			val ue = UpdateExternalOneToOneReverse[Any, Any](updateConfig, o, oldFT, newFT)
			val m = foreignEntity.oneToOneOnUpdateMap(oneToOneReverse).asInstanceOf[foreignEntity.OnUpdateOneToOneReverse[Any]]
			m(ue)
			ExternalEntityPersistedNode(foreignEntity, newFT) :: Nil
	}.flatten

	private def toNodes(cmds: List[PersistCmd]) =
		cmds.map {
			cmd =>
				toSql(cmd).map {
					sql =>
						Node(
							sql,
							cmd
						)
				}
		}.flatten

	protected[jdbc] def toSql(cmd: PersistCmd): List[driver.sqlBuilder.Result] =
		cmd match {
			case ic@InsertCmd(tpe, newVM, columns, _) =>
				persistedIdentities += ic.newVM.o

				// now we need to remove duplicates and also make sure that in case of
				// the same column having 2 values, we take the not-null one (if present)
				// see UseCaseHierarchicalCategoriesSuite
				val relatedColumns = prioritized.relatedColumns(newVM, false)
				val rcMap = relatedColumns.filterNot(_._2 == null).toMap
				val related = relatedColumns.map {
					case (c, v) =>
						(c, rcMap.getOrElse(c, v))
				}.distinct.filterNot(t => columns.contains(t))
				val converted = typeManager.transformValuesBeforeStoring(columns ::: related)
				driver.insertSql(updateConfig, tpe, converted).result :: Nil

			case uc@UpdateCmd(tpe, oldVM, newVM, columns, _) =>
				persistedIdentities += uc.newVM.o
				val oldRelated = prioritized.relatedColumns(oldVM, true)
				val newRelated = prioritized.relatedColumns(newVM, false)
				val set = columns ::: newRelated.filterNot(n => oldRelated.contains(n) || columns.contains(n)).distinct
				if (set.isEmpty)
					Nil
				else {
					val pks = oldVM.toListOfPrimaryKeyAndValueTuple(tpe)
					val relKeys = prioritized.relatedKeys(newVM)
					val keys = pks ::: relKeys
					val setConverted = typeManager.transformValuesBeforeStoring(set)
					val keysConverted = typeManager.transformValuesBeforeStoring(keys)
					driver.updateSql(updateConfig, tpe, setConverted, keysConverted).result :: Nil
				}

			case InsertManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
				driver.insertManyToManySql(updateConfig, manyToMany, left, right).result :: Nil

			case DeleteManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
				val left = manyToMany.linkTable.left zip entityVM.toListOfPrimaryKeys(tpe)
				val right = manyToMany.linkTable.right zip foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
				driver.deleteManyToManySql(updateConfig.deleteConfig, manyToMany, left, right).result :: Nil

			case dc@DeleteCmd(tpe, vm) =>
				persistedIdentities += vm.o
				val relatedKeys = prioritized.relatedKeys(vm)
				val args = vm.toListOfPrimaryKeyAndValueTuple(tpe) ::: relatedKeys
				driver.deleteSql(updateConfig.deleteConfig, tpe, args).result :: Nil

			case UpdateExternalManyToOneCmd(foreignEE, ci, newVM, fo) =>
				val ie = UpdateExternalManyToOne(updateConfig, fo)
				foreignEE.manyToOneOnUpdateMap(ci)(ie)
				Nil
			case UpdateExternalManyToManyCmd(tpe, newVM, foreignEntity, manyToMany, added, intersection, removed) =>
				val fTable = foreignEntity.tpe.table

				// removed
				val de = DeleteExternalManyToMany(updateConfig.deleteConfig, removed)
				foreignEntity.manyToManyOnUpdateMap(manyToMany)(de)

				val linkTable = manyToMany.column.linkTable
				val rSqls = removed.map {
					fo =>
						val left = linkTable.left zip newVM.toListOfPrimaryKeys(tpe)
						val right = linkTable.right zip fTable.toListOfPrimaryKeyValues(fo)
						driver.deleteManyToManySql(updateConfig.deleteConfig, manyToMany.column, left, right).result
				}.toList

				// updated
				val ue = UpdateExternalManyToMany(updateConfig, intersection)
				foreignEntity.manyToManyOnUpdateMap(manyToMany)(ue)

				// added
				val ie = InsertExternalManyToMany(updateConfig, added)
				foreignEntity.manyToManyOnUpdateMap(manyToMany)(ie)

				val aSqls = added.map {
					fo =>
						val left = newVM.toListOfPrimaryKeys(tpe)
						val right = fTable.toListOfPrimaryKeyValues(fo)
						driver.insertManyToManySql(updateConfig, manyToMany.column, left, right).result
				}.toList
				(rSqls ::: aSqls).toList
			case MockCmd(_, _, newVM) =>
				persistedIdentities += newVM.o
				Nil
			case InsertOneToManyExternalCmd(foreignEntity, oneToMany, entityVM, added) =>
				Nil
			case UpdateExternalOneToManyCmd(foreignEntity, oneToMany, entityVM, added, intersected, removed) =>
				Nil
			case InsertOneToOneReverseExternalCmd(tpe, foreignEntity, oneToOne, entityVM, ft) =>
				Nil
			case UpdateExternalOneToOneReverseCmd(tpe, foreignEntity, oneToOne, entityVM, oldFT, newFT) =>
				Nil
		}
}