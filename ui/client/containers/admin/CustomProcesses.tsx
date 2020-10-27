/* eslint-disable i18next/no-literal-string */
import React from "react"
import {Td, Tr} from "reactable"
import Date from "../../components/common/Date"
import HealthCheck from "../../components/HealthCheck"
import ProcessStateIcon from "../../components/Process/ProcessStateIcon"
import "../../stylesheets/processes.styl"
import tabStyles from "../../components/tabs/processTabs.styl"
import {Page} from "../Page"
import {Filterable, getProcessState, ProcessesList, RowsRenderer} from "../ProcessesList"
import {CancelIcon} from "./CancelIcon"
import {DeployIcon} from "./DeployIcon"

const ElementsRenderer: RowsRenderer = ({processes, getProcesses, statuses}) => {
  const processState = getProcessState(statuses)
  return processes.map(process => {
    return (
      <Tr className="row-hover" key={process.name}>
        <Td column="name">{process.name}</Td>
        <Td column="category">{process.processCategory}</Td>
        <Td column="createdAt" className="centered-column" value={process.createdAt}><Date date={process.createdAt}/></Td>
        <Td column="modifyDate" className="centered-column" value={process.modificationDate}><Date date={process.modificationDate}/></Td>
        <Td column="status" className="status-column">
          <ProcessStateIcon process={process} processState={processState(process)} isStateLoaded={!!statuses}/>
        </Td>
        <Td column="deploy" className="deploy-column">
          <DeployIcon process={process} processState={processState(process)} onDeploy={getProcesses}/>
        </Td>
        <Td column="cancel" className="cancel-column">
          <CancelIcon process={process} processState={processState(process)} onCancel={getProcesses}/>
        </Td>
      </Tr>
    )
  })
}

const sortable = ["name", "category", "modifyDate", "createdAt"]
const filterable: Filterable = ["name", "processCategory"]
const columns = [
  {key: "name", label: "Process name"},
  {key: "category", label: "Category"},
  {key: "createdAt", label: "Created at"},
  {key: "modifyDate", label: "Last modification"},
  {key: "status", label: "Status"},
  {key: "deploy", label: "Deploy"},
  {key: "cancel", label: "Cancel"},
]

export function CustomProcesses(): JSX.Element {

  return (
    <Page className={tabStyles.tabContentPage}>
      <HealthCheck/>
      <ProcessesList
        defaultQuery={{isCustom: true}}

        sortable={sortable}
        filterable={filterable}
        columns={columns}

        withStatuses

        RowsRenderer={ElementsRenderer}
      />
    </Page>
  )
}

export const CustomProcessesTabData = {
  header: "Custom Processes",
  key: "custom-processes",
  Component: CustomProcesses,
}
