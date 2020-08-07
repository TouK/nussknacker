import React, {useCallback} from "react"
import {useDispatch} from "react-redux"
import {showProcess} from "../actions/nk"
import {ProcessType} from "../components/Process/types"
import TableRowIcon from "../components/table/TableRowIcon"

export function EditItem({process}: {process: ProcessType}) {
  const dispatch = useDispatch()
  const clickHandler = useCallback(
    () => dispatch(showProcess(process.name)),
    [process],
  )
  return (
    <TableRowIcon glyph="edit" title="Edit process" onClick={clickHandler}/>
  )
}

export function ShowItem({process}: {process: ProcessType}) {
  const dispatch = useDispatch()
  const clickHandler = useCallback(
    () => dispatch(showProcess(process.name)),
    [process],
  )
  return (
    <TableRowIcon glyph="eye-open" title={`Show ${process.isSubprocess ? "subprocess" : "process"}`} onClick={clickHandler}/>
  )
}
