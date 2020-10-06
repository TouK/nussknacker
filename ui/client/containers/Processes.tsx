/* eslint-disable i18next/no-literal-string */
import classNames from "classnames"
import React from "react"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import ProcessStateIcon from "../components/Process/ProcessStateIcon"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import styles from "../containers/processesTable.styl"
import {EditItem} from "./editItem"
import {MetricsItem} from "./metricsItem"
import {Page} from "./Page"
import {getProcessState, ProcessesList, RowsRenderer} from "./ProcessesList"
import {ProcessNameInput} from "./ProcessNameInput"
import tabStyles from "../components/tabs/processTabs.styl"
import {SearchItem} from "./TableFilters"

const ElementsRenderer: RowsRenderer = ({processes, getProcesses, statuses}) => {
  const processState = getProcessState(statuses)
  return processes.map((process, index) => {
    return (
      <Tr key={index} className="row-hover">
        <Td column="name" className="name-column" value={process.name}>
          <ProcessNameInput process={process} onChange={getProcesses}/>
        </Td>
        <Td column="category">{process.processCategory}</Td>
        <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>
        <Td column="createdAt" className="centered-column" value={process.createdAt}>
          <Date date={process.createdAt}/>
        </Td>
        <Td column="modifyDate" className="centered-column" value={process.modificationDate}>
          <Date date={process.modificationDate}/>
        </Td>
        <Td column="status" className="status-column">
          <ProcessStateIcon
            process={process}
            processState={processState(process)}
            isStateLoaded={!!statuses}
          />
        </Td>
        <Td column="edit" className={classNames("edit-column", styles.iconOnly)}>
          <EditItem process={process}/>
        </Td>
        <Td column="metrics" className={classNames("metrics-column", styles.iconOnly)}>
          <MetricsItem process={process}/>
        </Td>
      </Tr>
    )
  })
}

const sortable = ["name", "category", "modifyDate", "createdAt", "createdBy"]
const filterable = ["name", "category", "createdBy"]
const columns = [
  {key: "name", label: "Name"},
  {key: "category", label: "Category"},
  {key: "createdBy", label: "Created by"},
  {key: "createdAt", label: "Created at"},
  {key: "modifyDate", label: "Last modification"},
  {key: "status", label: "Status"},
  {key: "edit", label: "Edit"},
  {key: "metrics", label: "Metrics"},
]

function Processes() {
  return (
    <Page className={tabStyles.tabContentPage}>
      <ProcessesList
        defaultQuery={{isSubprocess: false, isArchived: false}}
        searchItems={[SearchItem.categories, SearchItem.isDeployed]}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        withStatuses
        allowAdd

        RowsRenderer={ElementsRenderer}
      />
    </Page>
  )
}

export const ProcessesTabData = {
  path: `${nkPath}/processes`,
  header: "Processes",
  Component: Processes,
}
