/* eslint-disable i18next/no-literal-string */
import classNames from "classnames"
import React from "react"
import {Td, Tr} from "reactable"
import Date from "../components/common/Date"
import ProcessStateIcon from "../components/Process/ProcessStateIcon"
import "../stylesheets/processes.styl"
import styles from "../containers/processesTable.styl"
import {EditItem} from "./editItem"
import {MetricsItem} from "./metricsItem"
import {Page} from "./Page"
import {Filterable, getProcessState, ProcessesList, RowsRenderer} from "./ProcessesList"
import tabStyles from "../components/tabs/processTabs.styl"
import {SearchItem} from "./TableFilters"
import ProcessLastAction from "../components/Process/ProcessLastAction"
import {useTranslation} from "react-i18next"
import {BigEmoji} from "../common/startsWithEmoji"

const ElementsRenderer: RowsRenderer = ({processes, statuses}) => {
  const processState = getProcessState(statuses)

  return processes.map((process, index) => {
    return (
      <Tr key={index} className="row-hover">
        <Td column="name" className="name-column" value={process.name}><BigEmoji>{process.name}</BigEmoji></Td>
        <Td column="category">{process.processCategory}</Td>
        <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>
        <Td column="createdAt" className="centered-column" value={process.createdAt}>
          <Date date={process.createdAt}/>
        </Td>
        <Td column="modifyDate" className="centered-column" value={process.modificationDate}>
          <Date date={process.modificationDate}/>
        </Td>
        <Td column="lastAction" className="centered-column" value={process?.lastAction?.performedAt}>
          <ProcessLastAction process={process}/>
        </Td>
        <Td column="status" className="status-column" value={process.state.status.name}>
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

const sortable = ["name", "category", "modifyDate", "createdAt", "createdBy", "lastAction", "status"]
const filterable: Filterable = ["name", "processCategory", "createdBy"]

function Processes() {
  const {t} = useTranslation()
  const columns = [
    {key: "name", label: t("processList.name", "Name")},
    {key: "category", label: t("processList.category", "Category")},
    {key: "createdBy", label: t("processList.createdBy", "Created by")},
    {key: "createdAt", label: t("processList.createdAt", "Created at")},
    {key: "modifyDate", label: t("processList.modifyDate", "Last modification")},
    {key: "lastAction", label: t("processList.lastAction", "Last action")},
    {key: "status", label: t("processList.status", "Status")},
    {key: "edit", label: t("processList.edit", "Edit")},
    {key: "metrics", label: t("processList.metrics", "Metrics")},
  ]

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
  path: `/processes`,
  header: "Scenarios",
  Component: Processes,
}
