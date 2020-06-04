import {Table, Tr, Td} from "reactable"
import Date from "../components/common/Date"
import ProcessStateIcon from "../components/Process/ProcessStateIcon"
import React from "react"
import {MetricsItem} from "./metricsItem"
import {EditItem} from "./editItem"
import {getProcessState} from "./BaseProcesses"

type Props = {
  columns,
  handleBlur,
  onSort,
  state,
  onPageChange,
  changeProcessName,
  processNameChanged,
}

type State = {
  sort,
  statusesLoaded,
  processes,
  search,
  showLoader,
  page,
  statuses?,
}

export function getTable(props: Props, state: State) {
  const {columns, handleBlur, onSort, onPageChange, changeProcessName, processNameChanged} = props
  const {sort, statusesLoaded, processes, search, showLoader, page} = state
  const processState = getProcessState(state)
  const sortable = ["name", "category", "modifyDate", "createdAt", "createdBy"]
  const filterable = ["name", "category", "createdBy"]
  const filterBy = search.toLowerCase()
  const itemsPerPage = 8
  return (
    <Table
      className="esp-table"
      onSort={onSort}
      onPageChange={onPageChange}
      noDataText="No matching records found."
      hidden={showLoader}
      currentPage={page}
      defaultSort={sort}
      itemsPerPage={itemsPerPage}
      pageButtonLimit={5}
      previousPageLabel="<"
      nextPageLabel=">"
      sortable={sortable}
      filterable={filterable}
      hideFilterInput
      filterBy={filterBy}
      columns={columns}
    >
      {processes.map((process, index) => {
        return (
          <Tr key={index} className="row-hover">
            <Td column="name" className="name-column" value={process.name}>
              <input
                value={process.editedName != null ? process.editedName : process.name}
                className="transparent"
                onKeyPress={(event) => changeProcessName(process, event)}
                onChange={(event) => processNameChanged(process.name, event)}
                onBlur={(event) => handleBlur(process, event)}
              />
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
                isStateLoaded={statusesLoaded}
              />
            </Td>
            <Td column="edit" className="edit-column">
              <EditItem process={process}/>
            </Td>
            <Td column="metrics" className="metrics-column">
              <MetricsItem process={process}/>
            </Td>
          </Tr>
        )
      })}
    </Table>
  )
}
