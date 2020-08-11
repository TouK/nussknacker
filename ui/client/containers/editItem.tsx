import React from "react"
import {ProcessType} from "../components/Process/types"
import TableRowIcon from "../components/table/TableRowIcon"
import {ProcessLink} from "./processLink"

export function EditItem({process}: {process: ProcessType}) {
  return (
    <ProcessLink processId={process.name}>
      <TableRowIcon glyph="edit" title="Edit process"/>
    </ProcessLink>
  )
}

export function ShowItem({process}: {process: ProcessType}) {
  return (
    <ProcessLink processId={process.name}>
      <TableRowIcon glyph="eye-open" title={`Show ${process.isSubprocess ? "subprocess" : "process"}`}/>
    </ProcessLink>
  )
}
