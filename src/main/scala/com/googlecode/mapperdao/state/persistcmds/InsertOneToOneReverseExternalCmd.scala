package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao.{ValuesMap, ExternalEntity}
import com.googlecode.mapperdao.state.prioritise.Priority
import com.googlecode.mapperdao.schema.{Type, ColumnInfoOneToOneReverse}

/**
 * @author: kostas.kougios
 *          Date: 28/02/13
 */
case class InsertOneToOneReverseExternalCmd[ID, T, FID, FT](
	tpe: Type[ID, T],
	foreignEntity: ExternalEntity[FID, FT],
	oneToMany: ColumnInfoOneToOneReverse[ID, FID, FT],
	entityVM: ValuesMap,
	ft: FT
	) extends PersistCmd
{
	def priority = Priority.Low
}
