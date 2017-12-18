import React from 'react'
import { connect } from 'react-redux';
import _ from "lodash";
import {Table, Thead, Th, Tr, Td} from "reactable";
import { Tab, Tabs, TabList, TabPanel } from 'react-tabs';
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import ProcessUtils from "../common/ProcessUtils";

import 'react-tabs/style/react-tabs.css';
import filterIcon from '../assets/img/search.svg'

class AdminPage extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      processes: [],
      componentIds: [],
      unusedComponents: [],
    }
  }

  componentDidMount() {
    HttpService.fetchProcesses().then ((processes) => {
      HttpService.fetchSubProcesses().then((subProcesses) =>{
        this.setState({
          processes: _.concat(processes, subProcesses)
        })
      })
    })
    HttpService.fetchComponentIds().then((componentIds) => {
      this.setState({componentIds: componentIds})
    })
    HttpService.fetchUnusedComponents().then((unusedComponents) => {
      this.setState({unusedComponents: unusedComponents})
    })
  }

  render() {
    const tabs = [
      {tabName: "Search components", component: <ProcessSearch componentIds={this.state.componentIds} processes={this.state.processes}/>},
      {tabName: "Unused components", component: <UnusedComponents unusedComponents={this.state.unusedComponents}/>},
    ]
    return (
      <div className="Page">
        <Tabs>
          <TabList>
            {_.map(tabs, (tab, i) => {
              return (<Tab key={i}>{tab.tabName}</Tab>)
            })}
          </TabList>
          {_.map(tabs, (tab, i) => {
            return (<TabPanel key={i}>{tab.component}</TabPanel>)
          })}
        </Tabs>
      </div>
    )
  }

}

AdminPage.path = "/admin"
AdminPage.header = "Admin"

function mapState(state) {
  return {
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(AdminPage);

class ProcessSearch extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      componentToFind: null,
      filterVal: ''
    }
  }

  render() {
    const found = _.map(ProcessUtils.search(this.props.processes, this.state.componentToFind), (result) => {
      return {
        Process: result.process.id,
        Node: result.node.id,
        Category: result.process.processCategory
      }
    })
    return (
      <div>
        <select className="table-select" onChange={(e) => this.setState({componentToFind: e.target.value})}>
          <option disabled selected value> -- select an option --</option>
          {this.props.componentIds.map((componentId, index) => {
            return (<option key={index} value={componentId}>{componentId}</option>)
          })}
        </select>
        <div id="table-filter" className="input-group">
          <input type="text" className="form-control" aria-describedby="basic-addon1"
                 value={this.state.filterVal} onChange={(e) => this.setState({filterVal: e.target.value})}/>
          <span className="input-group-addon" id="basic-addon1">
            <img id="search-icon" src={filterIcon}/>
          </span>
        </div>
        {this.componentToFindChosen() ?
          <Table className="esp-table" data={found}
                 sortable={['Process', 'Node', 'Category']}
                 filterable={['Process', 'Node', 'Category']}
                 noDataText="No matching records found."
                 hideFilterInput
                 filterBy={this.state.filterVal.toLowerCase()}
          /> : null
        }
      </div>
    )
  }

  componentToFindChosen = () => {
    return !_.isEmpty(this.state.componentToFind)
  }

}

class UnusedComponents extends React.Component {

  render() {
    const emptyComponentsToRender = _.map(this.props.unusedComponents, (componentId) => {return {ComponentId: componentId}})
    return (
      <div>
        <br/>
        <Table className="esp-table" data={emptyComponentsToRender} hideFilterInput/>
      </div>
    )
  }

}