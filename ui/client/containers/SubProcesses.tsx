import React from "react"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import {ProcessType} from "../components/Process/types"
import TableRowIcon from "../components/table/TableRowIcon"
import "../stylesheets/processes.styl"
import {Page} from "./Page"
import {ProcessesTabData} from "./Processes"
import {ProcessesList, RowsRenderer} from "./ProcessesList"
import {ProcessLink} from "./processLink"
import tabStyles from "../components/tabs/processTabs.styl"
import {SearchItem} from "./TableFilters"

function ShowProcessIcon({process}: {process: ProcessType}) {
  return (
    <ProcessLink processId={process.name}>
      <TableRowIcon glyph="edit" title="Edit subprocess"/>
    </ProcessLink>
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
    <Page className={tabStyles.tabContentPage}>
      <ProcessesList
        defaultQuery={{isSubprocess: true, isArchived: false}}
        searchItems={[SearchItem.categories]}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        allowAdd

        RowsRenderer={ElementsRenderer}
      >
      </ProcessesList>
    </Page>
  )
}

export const SubProcessesTabData = {
  path: `${ProcessesTabData.path}/subprocesses`,
  header: "Subprocesses",
  Component: SubProcesses,
}
