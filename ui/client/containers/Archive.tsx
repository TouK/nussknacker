import classNames from "classnames"
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import "../stylesheets/processes.styl"
import styles from "../containers/processesTable.styl"
import {ShowItem} from "./editItem"
import {Page} from "./Page"
import {ProcessesTabData} from "./Processes"
import {Filterable, ProcessesList, RowsRenderer} from "./ProcessesList"
import tabStyles from "../components/tabs/processTabs.styl"
import {SearchItem} from "./TableFilters"

const ElementsRenderer: RowsRenderer = ({processes}) => processes.map(process => (
  <Tr className="row-hover" key={process.name}>
    <Td column="name">{process.name}</Td>
    <Td column="category">{process.processCategory}</Td>
    <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>
    <Td column="createdAt" className="centered-column" value={process.createdAt}><Date date={process.createdAt}/></Td>
    <Td column="actionDate" className="centered-column" value={process?.lastAction?.performedAt}><Date date={process?.lastAction?.performedAt}/></Td>
    <Td column="actionUser" className="centered-column" value={process?.lastAction?.user}>{process?.lastAction?.user}</Td>
    <Td column="subprocess" className="centered-column" value={process.isSubprocess}>
      <Glyphicon glyph={process.isSubprocess ? "ok" : "remove"}/>
    </Td>
    <Td column="view" className={classNames("edit-column", styles.iconOnly)}>
      <ShowItem process={process}/>
    </Td>
  </Tr>
))

const sortable = ["name", "category", "modifyDate", "createdAt", "createdBy", "subprocess", "actionDate", "actionUser"]
const filterable: Filterable = ["name", "processCategory", "createdBy"]
const columns = [
  {key: "name", label: "Process name"},
  {key: "category", label: "Category"},
  {key: "createdBy", label: "Created by"},
  {key: "createdAt", label: "Created at"},
  {key: "actionDate", label: "Archived at"},
  {key: "actionUser", label: "Archived by"},
  {key: "subprocess", label: "Subprocess"},
  {key: "view", label: "View"},
]

function Archive() {
  return (
    <Page className={tabStyles.tabContentPage}>
      <ProcessesList
        defaultQuery={{isArchived: true}}
        searchItems={[SearchItem.categories, SearchItem.isSubprocess]}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        RowsRenderer={ElementsRenderer}
      />
    </Page>
  )
}

export const ArchiveTabData = {
  path: `${ProcessesTabData.path}/archived`,
  header: "Archive",
  Component: Archive,
}
