/* eslint-disable i18next/no-literal-string */
import React, {useCallback} from "react"
import {useDispatch} from "react-redux"
import {Td, Tr} from "reactable"
import {showProcess} from "../actions/nk"
import Date from "../components/common/Date"
import {ProcessType} from "../components/Process/types"
import TableRowIcon from "../components/table/TableRowIcon"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import {PageWithHealthCheck} from "./Page"
import {ProcessesList, RowsRenderer} from "./ProcessesList"
import {SearchItem} from "./TableFilters"

function ShowProcessIcon({process}: {process: ProcessType}) {
  const dispatch = useDispatch()
  const onClick = useCallback(() => dispatch(showProcess(process.name)), [process.name])
  return (
    <TableRowIcon glyph="edit" title="Edit subprocess" onClick={onClick}/>
  )
}

const ElementsRenderer: RowsRenderer = ({processes}) => processes.map(process => (
  <Tr className="row-hover" key={process.name}>
    <Td column="name">{process.name}</Td>,
    <Td column="category">{process.processCategory}</Td>,
    <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>,
    <Td column="createdAt" className="centered-column" value={process.createdAt}><Date date={process.createdAt}/></Td>,
    <Td column="modifyDate" className="centered-column" value={process.modificationDate}><Date date={process.modificationDate}/></Td>,
    <Td column="edit" className="edit-column"><ShowProcessIcon process={process}/></Td>,
  </Tr>
))

const sortable = ["name", "category", "modifyDate", "createDate", "createdBy"]
const filterable = ["name", "category", "createdBy"]
const columns = [
  {key: "name", label: "Process name"},
  {key: "category", label: "Category"},
  {key: "createdBy", label: "Created by"},
  {key: "createdAt", label: "Created"},
  {key: "modifyDate", label: "Last modification"},
  {key: "edit", label: "Edit"},
]

function SubProcesses() {
  return (
    <PageWithHealthCheck>
      <ProcessesList
        defaultQuery={{isSubprocess: true, isArchived: false}}
        searchItems={[SearchItem.categories]}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        allowAdd

        RowsRenderer={ElementsRenderer}
      />
    </PageWithHealthCheck>
  )
}

export const SubProcessesTabData = {
  path: `${nkPath}/subProcesses`,
  header: "Subprocesses",
  Component: SubProcesses,
}
