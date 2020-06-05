/* eslint-disable i18next/no-literal-string */
import React from "react"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import Date from "../components/common/Date"
import LoaderSpinner from "../components/Spinner"
import TableRowIcon from "../components/table/TableRowIcon"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import {BaseProcesses, BaseProcessesOwnProps, baseMapState} from "./BaseProcesses"
import {showProcess} from "../actions/nk"
import {PageWithHealthCheck} from "./Page"
import {RouteComponentProps} from "react-router"
import {TableFilters} from "./TableFilters"
import {ProcessTableTools} from "./processTableTools"

const SubProcessesPage = () => {
  return (
    <SubProcessesComponent
      queries={{
        isSubprocess: true,
        isArchived: false,
      }}
      page={"subProcesses"}
    />
  )
}

export const path = `${nkPath}/subProcesses`
export const header = "Subprocesses"

class SubProcesses extends BaseProcesses<Props> {
  render() {
    const {onPageChange, onSort} = this
    const {processes, sort, showLoader, page, search} = this.state
    const {showProcess} = this.props

    return (
      <PageWithHealthCheck>
        <ProcessTableTools allowAdd isSubprocess>
          <TableFilters
            filters={this.searchItems}
            value={this.state}
            onChange={this.onFilterChange}
          />
        </ProcessTableTools>

        <LoaderSpinner show={showLoader}/>

        <Table
          className="esp-table"
          onSort={onSort}
          onPageChange={onPageChange}
          noDataText="No matching records found."
          hidden={showLoader}
          currentPage={page}
          defaultSort={sort}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          sortable={["name", "category", "modifyDate", "createDate", "createdBy"]}
          filterable={["name", "category", "createdBy"]}
          hideFilterInput
          filterBy={search.toLowerCase()}
          columns={[
            {key: "name", label: "Process name"},
            {key: "category", label: "Category"},
            {key: "createdBy", label: "Created by"},
            {key: "createdAt", label: "Created"},
            {key: "modifyDate", label: "Last modification"},
            {key: "edit", label: "Edit"},
          ]}
        >
          {processes.map((process, index) => {
            return (
              <Tr className="row-hover" key={index}>
                <Td column="name">{process.name}</Td>
                <Td column="category">{process.processCategory}</Td>
                <Td column="createdBy" className="centered-column" value={process.createdBy}>{process.createdBy}</Td>
                <Td column="createdAt" className="centered-column" value={process.createdAt}>
                  <Date date={process.createdAt}/>
                </Td>
                <Td column="modifyDate" className="centered-column" value={process.modificationDate}>
                  <Date date={process.modificationDate}/>
                </Td>
                <Td column="edit" className="edit-column">
                  <TableRowIcon
                    glyph="edit"
                    title="Edit subprocess"
                    onClick={() => showProcess(process.name)}
                  />
                </Td>
              </Tr>
            )
          })}
        </Table>
      </PageWithHealthCheck>
    )
  }

  static path = path
  static header = header
}

const mapDispatch = {showProcess}

type StateProps = ReturnType<typeof baseMapState> & typeof mapDispatch
type Props = StateProps & RouteComponentProps & BaseProcessesOwnProps

const SubProcessesComponent = withRouter(connect(baseMapState, mapDispatch)(SubProcesses))
export const Component = SubProcessesPage
