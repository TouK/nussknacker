import TableRowIcon from "../components/table/TableRowIcon"
import {useDispatch} from "react-redux"
import React, {useCallback} from "react"
import {showProcess} from "../actions/nk"

export function EditItem({process}: { process: any }) {
  const dispatch = useDispatch()
  const clickHandler = useCallback(
    () => dispatch(showProcess(process.name)),
    [process],
  )
  return (
    <TableRowIcon glyph="edit" title="Edit process" onClick={clickHandler}/>
  )
}
