/* eslint-disable i18next/no-literal-string */
import React from "react"
import {Glyphicon} from "react-bootstrap"
import {connect} from "react-redux"
import {withRouter, RouteComponentProps} from "react-router-dom"
import {Table, Td, Tr} from "reactable"
import * as DialogMessages from "../../common/DialogMessages"
import Date from "../../components/common/Date"
import LoaderSpinner from "../../components/Spinner"
import HttpService from "../../http/HttpService"
import "../../stylesheets/processes.styl"
import {BaseProcesses, getProcessState, BaseProcessesOwnProps, baseMapState} from "../BaseProcesses"
import ProcessStateIcon from "../../components/Process/ProcessStateIcon"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"
import {PageWithHealthCheck} from "../Page"
import {toggleConfirmDialog} from "../../actions/nk"
import {TableFilters} from "../TableFilters"
import {ProcessTableTools} from "../processTableTools"

export const CustomProcessesPage = () => {
  return (
    <CustomProcessesComponent
      searchItems={[]}
      page={"custom"}
      shouldReloadStatuses={true}
      defaultState={{
        statusesLoaded: false,
        statuses: {},
      }}
    />
  )
}

export const header = "Custom Processes"
export const key = "custom-processes"

class CustomProcesses extends BaseProcesses<Props> {
  reloadProcesses(shouldShowLoader = true) {
    this.showLoader(shouldShowLoader)

    HttpService.fetchCustomProcesses().then(response => {
      this.setState({processes: response.data, showLoader: false})
    }).catch(() => this.showLoader(false))
  }

  deploy = process => () => {
    this.props.toggleConfirmDialog(true, DialogMessages.deploy(process.name), () => {
      return HttpService.deploy(process.name).finally(() => this.reload())
    })
  }

  cancel = process => () => {
    this.props.toggleConfirmDialog(true, DialogMessages.stop(process.name), () => {
      return HttpService.cancel(process.name).finally(() => this.reload())
    })
  }

  render() {
    const {cancel, onSort, onPageChange, deploy} = this
    const processState = getProcessState(this.state)
    const {sort, statusesLoaded, processes, showLoader, page, search} = this.state

    return (
      <PageWithHealthCheck>
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
          sortable={["name", "category", "modifyDate", "createdAt"]}
          filterable={["name", "category"]}
          hideFilterInput
          filterBy={search.toLowerCase()}
          columns={[
            {key: "name", label: "Process name"},
            {key: "category", label: "Category"},
            {key: "createdAt", label: "Created at"},
            {key: "modifyDate", label: "Last modification"},
            {key: "status", label: "Status"},
            {key: "deploy", label: "Deploy"},
            {key: "cancel", label: "Cancel"},
          ]}
        >
          {
            processes.map((process, index) => {
              return (
                <Tr className="row-hover" key={index}>
                  <Td column="name">{process.name}</Td>
                  <Td column="category">{process.processCategory}</Td>
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
                  <Td column="deploy" className="deploy-column">
                    {ProcessStateUtils.canDeploy(processState(process)) ? (
                      <Glyphicon glyph="play" title="Deploy process" onClick={deploy(process)}/>
                    ) : null
                    }
                  </Td>
                  <Td column="cancel" className="cancel-column">
                    {ProcessStateUtils.canCancel(processState(process)) ? (
                      <Glyphicon glyph="stop" title="Cancel process" onClick={cancel(process)}/>
                    ) : null
                    }
                  </Td>
                </Tr>
              )
            })}
        </Table>
      </PageWithHealthCheck>
    )
  }

  static header = header
  static key = key
}

const mapDispatch = {toggleConfirmDialog}

type StateProps = typeof mapDispatch & ReturnType<typeof baseMapState> & RouteComponentProps
type Props = StateProps & RouteComponentProps & BaseProcessesOwnProps

const CustomProcessesComponent = withRouter(connect(baseMapState, mapDispatch)(CustomProcesses))

