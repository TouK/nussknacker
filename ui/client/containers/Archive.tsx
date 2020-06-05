/* eslint-disable i18next/no-literal-string */
import * as queryString from "query-string"
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import Date from "../components/common/Date"
import LoaderSpinner from "../components/Spinner"
import TableRowIcon from "../components/table/TableRowIcon"
import {nkPath} from "../config"
import "../stylesheets/processes.styl"
import {BaseProcesses, BaseProcessesOwnProps, baseMapState} from "./BaseProcesses"
import {showProcess} from "../actions/nk/showProcess"
import history from "../history"
import {Page} from "./Page"
import {RouteComponentProps} from "react-router"
import {SearchItem, TableFilters} from "./TableFilters"
import {ProcessTableTools} from "./processTableTools"

export const path = `${nkPath}/archivedProcesses`
export const header = "Archive"

const ArchivePage = () => {
  const {isSubprocess} = queryString.parse(history.location.search, {parseBooleans: true})
  return (
    <ArchiveComponent
      queries={{isArchived: true}}
      searchItems={[SearchItem.categories, SearchItem.isSubprocess]}
      page={"archive"}
      defaultState={{
        isSubprocess: isSubprocess as boolean,
      }}
    />
  )
}

class Archive extends BaseProcesses<Props> {
  render() {
    const {showLoader, processes, sort, page, search} = this.state
    const {showProcess} = this.props
    const onSort = sort => this.setState({sort})
    const onPageChange = page => this.setState({page})

    return (
      <Page>
        <ProcessTableTools>
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
          sortable={["name", "category", "modifyDate"]}
          filterable={["name", "category"]}
          hideFilterInput
          filterBy={search.toLowerCase()}
          columns={[
            {key: "name", label: "Process name"},
            {key: "category", label: "Category"},
            {key: "subprocess", label: "Subprocess"},
            {key: "modifyDate", label: "Last modification"},
            {key: "view", label: "View"},
          ]}
        >
          {
            processes.map((process, index) => {
              return (
                <Tr className="row-hover" key={index}>
                  <Td column="name">{process.name}</Td>
                  <Td column="category">{process.processCategory}</Td>
                  <Td column="subprocess" className="centered-column">
                    <Glyphicon glyph={process.isSubprocess ? "ok" : "remove"}/>
                  </Td>
                  <Td
                    column="modifyDate"
                    className="centered-column"
                    value={process.modificationDate}
                  >
                    <Date date={process.modificationDate}/>
                  </Td>
                  <Td column="view" className="edit-column">
                    <TableRowIcon
                      glyph="eye-open"
                      title={`Show ${process.isSubprocess ? "subprocess" : "process"}`}
                      onClick={() => showProcess(process.name)}
                    />
                  </Td>
                </Tr>
              )
            })}
        </Table>
      </Page>
    )
  }

  static path = path
  static header = header
}

const mapDispatch = {showProcess}

type StateProps = ReturnType<typeof baseMapState> & typeof mapDispatch
type Props = StateProps & RouteComponentProps & BaseProcessesOwnProps

const ArchiveComponent = withRouter(connect(baseMapState, mapDispatch)(Archive))
export const Component = ArchivePage
