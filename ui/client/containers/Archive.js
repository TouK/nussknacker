import React from "react"
import {Table, Td, Tr} from "reactable"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import DateUtils from "../common/DateUtils"
import LoaderSpinner from "../components/Spinner.js"
import * as  queryString from 'query-string'
import "../stylesheets/processes.styl"
import filterIcon from '../assets/img/search.svg'
import {withRouter} from 'react-router-dom'
import {Glyphicon} from 'react-bootstrap'
import BaseProcesses from "./BaseProcesses"
import Select from 'react-select'
import ProcessUtils from "../common/ProcessUtils"
import {nkPath} from "../config";

class Archive extends BaseProcesses {
  queries = {
    isArchived: true
  }

  searchItems = ['categories', 'isSubprocess']

  constructor(props) {
    super(props)

    const query = queryString.parse(this.props.history.location.search, {parseBooleans: true})

    this.state = Object.assign({
      selectedIsSubrocess: _.find(this.filterIsSubprocessOptions, {value: query.isSubprocess})
    }, this.prepareState())
  }

  render() {
    return (
      <div className="Page">
        <div id="process-top-bar">
          <div id="table-filter" className="input-group">
            <input
              type="text"
              className="form-control"
              aria-describedby="basic-addon1"
              value={this.state.search}
              onChange={this.onSearchChange}
            />
            <span className="input-group-addon" id="basic-addon1">
              <img id="search-icon" src={filterIcon}/>
            </span>
          </div>

          <div id="categories-filter" className="input-group">
            <Select
              isMulti
              isSearchable
              defaultValue={this.state.selectedCategories}
              closeMenuOnSelect={false}
              id="categories"
              className="form-select"
              options={this.props.filterCategories}
              placeholder="Select categories.."
              onChange={this.onCategoryChange}
              styles={this.customSelectStyles}
              theme={this.customSelectTheme}
            />
          </div>

          <div id="subprocess-types-filter" className="input-group">
            <Select
              className="form-select"
              defaultValue={this.state.selectedIsSubrocess}
              options={this.filterIsSubprocessOptions}
              placeholder="Select process type.."
              onChange={this.onIsSubprocessChange}
              styles={this.customSelectStyles}
              theme={this.customSelectTheme}
            />
          </div>
        </div>

        <LoaderSpinner show={this.state.showLoader}/>

        <Table
          className="esp-table"
          onSort={sort => this.setState({sort: sort})}
          onPageChange={page => this.setState({page: page})}
          noDataText="No matching records found."
          hidden={this.state.showLoader}
          currentPage={this.state.page}
          defaultSort={this.state.sort}
          itemsPerPage={10}
          pageButtonLimit={5}
          previousPageLabel="<"
          nextPageLabel=">"
          sortable={['name', 'category', 'modifyDate']}
          filterable={['name', 'category']}
          hideFilterInput
          filterBy={this.state.search.toLowerCase()}
          columns={[
            {key: 'name', label: 'Process name'},
            {key: 'category', label: 'Category'},
            {key: 'subprocess', label: 'Subprocess'},
            {key: 'modifyDate', label: 'Last modification'},
            {key: 'view', label: 'View'},
          ]}
        >
          {
            this.state.processes.map((process, index) => {
              return (
                <Tr className="row-hover" key={index}>
                  <Td column="name">{process.name}</Td>
                  <Td column="category">{process.processCategory}</Td>
                  <Td column="subprocess" className="centered-column">
                    <Glyphicon glyph={process.isSubprocess ? 'ok' : 'remove'}/>
                  </Td>
                  <Td column="modifyDate" className="centered-column">{DateUtils.format(process.modificationDate)}</Td>
                  <Td column="view" className="edit-column">
                    <Glyphicon
                      glyph="eye-open"
                      title={"Show " + (process.isSubprocess ? "subprocess" : "process")}
                      onClick={this.showProcess.bind(this, process)}
                    />
                  </Td>
                </Tr>
              )
            })}
        </Table>
      </div>
    )
  }
}

Archive.path = `${nkPath}/archivedProcesses`
Archive.header = 'Archive'

const mapState = (state) => ({
  loggedUser: state.settings.loggedUser,
  featuresSettings: state.settings.featuresSettings,
  filterCategories: ProcessUtils.prepareFilterCategories(state.settings.loggedUser.categories, state.settings.loggedUser)
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Archive))