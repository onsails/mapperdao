package com.googlecode.mapperdao.state.recreation

import com.googlecode.mapperdao.state.persisted.PersistedNode
import com.googlecode.mapperdao._

/**
 * during recreation phase, persisted objects are re-created with PC included.
 *
 * @author kostantinos.kougios
 *
 * 11 Dec 2012
 */
class RecreationPhase(
		updateConfig: UpdateConfig,
		mockFactory: MockFactory,
		typeManager: TypeManager,
		entityMap: UpdateEntityMap) {

	def execute[ID, T](nodes: List[PersistedNode[ID, T]]) =
		nodes.map { node =>
			entityMap.get[DeclaredIds[ID], T](node.identity).getOrElse {
				val entity = node.entity
				val tpe = entity.tpe
				val table = tpe.table

				val newVM = node.newVM
				val modified = newVM.toMap

				// create a mock
				var mockO = mockFactory.createMock(updateConfig.data, entity, modified)
				entityMap.put(node.identity, mockO)

				val ur = node.generatedKeys.toMap

				val keysMap = table.simpleTypeAutoGeneratedColumns.map { c =>
					val ag = ur(c)
					// many drivers return the wrong type for the autogenerated
					// keys, typically instead of Int they return Long
					table.pcColumnToColumnInfoMap(c) match {
						case ci: ColumnInfo[_, _] =>
							val fixed = typeManager.toActualType(ci.dataType, ag)
							(c.name, fixed)
					}
				}.toMap

				val finalMods = modified ++ keysMap
				val newE = tpe.constructor(updateConfig.data, ValuesMap.fromMap(node.identity, finalMods))
				// re-put the actual
				entityMap.put(node.identity, newE)
				newE
			}
		}

}