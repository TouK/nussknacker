import React from 'react'
import {connect} from 'react-redux';
import _ from "lodash";
import {Table, Td, Tr} from "reactable";
import {Tab, TabList, TabPanel, Tabs} from 'react-tabs';
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import ProcessUtils from "../common/ProcessUtils";
import * as JsonUtils from '../common/JsonUtils';
import JSONTree from 'react-json-tree'
import * as VisualizationUrl from '../common/VisualizationUrl'
import LoaderSpinner from "../components/Spinner.js";

import 'react-tabs/style/react-tabs.css';
import filterIcon from '../assets/img/search.svg'
import CustomProcesses from "./CustomProcesses";

class AdminPage extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      componentIds: [],
      unusedComponents: [],
      services:{}
    }
  }

  componentDidMount() {
    HttpService.fetchComponentIds().then((componentIds) => {
      this.setState({componentIds: componentIds})
    })
    HttpService.fetchUnusedComponents().then((unusedComponents) => {
      this.setState({unusedComponents: unusedComponents})
    })
    HttpService.fetchServices().then((services) => {
      this.setState({
        services: services
      })
    })
  }

  render() {
    const tabs = [
      {tabName: "Search components", component: <ProcessSearch componentIds={this.state.componentIds} />},
      {tabName: "Unused components", component: <UnusedComponents unusedComponents={this.state.unusedComponents}/>},
      {tabName: "Services", component: <TestServices componentIds={this.state.componentIds} services={this.state.services}/>},
      {tabName: CustomProcesses.title, component: <CustomProcesses/>}
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
  return {}
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(AdminPage);

class ProcessSearch extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      processes: [],
      processesLoaded: false,
      componentToFind: null,
      filterVal: '',
      currentPage: 1
    }
  }

  onComponentChange(componentToFind) {
    this.setState({processesLoaded: false, componentToFind: componentToFind})
    HttpService.fetchProcessesComponents(componentToFind).then((processesComponents) => {
      console.log(processesComponents)
      this.setState({
          processes: _.map(processesComponents, (result) => {
            return {
              processName: result.processName,
              nodeId: result.nodeId,
              processCategory: result.processCategory,
              isDeployed: result.isDeployed
            }
          }),
          processesLoaded: true
        })
    })
  }

  render() {
    return (
      <div>
        <select className="table-select" onChange={(e) => this.onComponentChange(e.target.value)} value={this.state.componentToFind || 0}>
          <option disabled key={0} value={0}>-- select an option --</option>
          {
            this.props.componentIds.map((componentId, index) => {
              return (<option key={index} value={componentId}>{componentId}</option>)
            }
          )}
        </select>
        <div id="table-filter" className="input-group">
          <input type="text" className="form-control" aria-describedby="basic-addon1"
                 value={this.state.filterVal} onChange={(e) => this.setState({filterVal: e.target.value})}/>
          <span className="input-group-addon" id="basic-addon1">
            <img id="search-icon" src={filterIcon}/>
          </span>
        </div>
        {this.componentToFindChosen() ?
          this.state.processesLoaded ?
            <Table className="esp-table"
                   sortable={['processName', 'nodeId', 'processCategory']}
                   filterable={['processName', 'nodeId', 'processCategory']}
                   noDataText="No matching records found."
                   hideFilterInput
                   onPageChange={currentPage => this.setState({currentPage: currentPage})}
                   currentPage={this.state.currentPage}
                   itemsPerPage={10}
                   pageButtonLimit={5}
                   previousPageLabel="<"
                   nextPageLabel=">"
                   filterBy={this.state.filterVal.toLowerCase()}
                   columns={[
                     {key: "processName", label: "Process"},
                     {key: "nodeId", label: "Node"},
                     {key: "processCategory", label: "Category"},
                     {key: "isDeployed", label: "Is deployed"},
                   ]}
            >
              {this.state.processes.map((row, idx) => {
                return (
                  <Tr key={idx}>
                    <Td column="processName">{row.processName}</Td>
                    <Td column="nodeId">
                      <a target="_blank" href={VisualizationUrl.visualizationUrl(row.processName, row.nodeId)}>
                        {row.nodeId}
                      </a>
                    </Td>
                    <Td column="processCategory">{row.processCategory}</Td>
                    <Td column="isDeployed">
                      <div className={row.isDeployed ? "status-running" : null}/>
                    </Td>
                  </Tr>
                )
              })}

            </Table> : <LoaderSpinner show={true}/>
         : null
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
    const emptyComponentsToRender = _.map(this.props.unusedComponents, (componentId) => {
      return {'Component ID': componentId}
    })
    return (
      <div>
        <br/>
        <Table className="esp-table" data={emptyComponentsToRender} hideFilterInput/>
      </div>
    )
  }

}

export function mapProcessDefinitionToServices(services) {
  return _.sortBy(
    _.flatMap(services, (typeServices, processingType) =>
        _.map(typeServices, (service, name) => (
          {
            name: name,
            categories: service.categories,
            parameters: _.map(service.parameters, p => (
              {
                name: p.name,
                refClazzName:
                p.typ.refClazzName
              }
            )),
            returnClassName: service.returnType.refClazzName,
            processingType: processingType
          }
        )
      )
    ), s => s.name
  );
}

class TestServices extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      processingType: '',
      serviceName: '',
      nodeParameters: {},
      parametersValues: [],
      queryResult: {
        response: {},
        errorMessage: null
      }

    };
    this.services = mapProcessDefinitionToServices(this.props.services);
  }

  componentDidMount(){
    if (!_.isEmpty(this.services)) {
      this.setService(0)
    }
  }

  setService(idx){
    const service = this.services[idx];

    const cachedParams = this.cachedServiceParams(service.name, service.processingType);

    const initializeParameter = paramName => _.find(cachedParams, cp => cp.name === paramName) || {
      "name": paramName,
      "expression": {
        //TODO: is it always fixed?
        "language": "spel",
        "expression": ""
      }
    };

    const initializeParametersValues = params => _.map(params, p => initializeParameter(p.name));
    this.setState(
      {
        processingType: service.processingType,
        serviceName: service.name,
        nodeParameters: service.parameters,
        parametersValues: initializeParametersValues(service.parameters||[]),
        queryResult: {
          response: {},
          errorMessage: null
        }
      })
  }

  serviceList() {
    return (
        <select className="node-input" onChange={e => this.setService(e.target.value)}>
          {this.services.map((service, idx) =>
            <option key={idx} value={idx}>{service.name}</option>)}
        </select>
    )
  }

  //TODO: use NodeDetailsContent (after NDC refactor)
  parametersList(params) {
    const setParam = paramName => value => {
      const params = this.state.parametersValues;
      _.find(params, p => p.name === paramName)
        .expression
        .expression = value;
      this.setState({parametersValues: params})
    };
    return (
      <span>
        {_.map(params, (param) =>
          this.formRow(
            "param_" + param.name,
            <span>{param.name}<div className="labelFooter">{ProcessUtils.humanReadableType(param.refClazzName)}</div></span>,
            <span>
              <input
                className="node-input"
                value={this.findParamExpression(param.name)}
                onChange={e => setParam(param.name)(e.target.value)}
              />
            </span>
          )
        )}
        </span>
    )
  }

  invokeService() {
    const showResponse = r => {
      if (r.status === 500) {
        r.json().then(error => this.setState({queryResult: {response: {}, errorMessage: error.message}}))
      } else {
        this.cacheServiceParams(this.state.serviceName, this.state.processingType, this.state.parametersValues)
        r.json().then(response => this.setState({queryResult: {response: response, errorMessage: null}}))
      }
    }

    HttpService.invokeService(
      this.state.processingType,
      this.state.serviceName,
      this.state.parametersValues
    ).then(showResponse)
  }

  paramsCacheKey(serviceName, processingType) { return `${serviceName}:${processingType}:parameters` }

  cachedServiceParams(serviceName, processingType) {
    const key = this.paramsCacheKey(serviceName, processingType)
    const cached = localStorage.getItem(key)

    if (cached) return JSON.parse(cached)
  }

  cacheServiceParams(serviceName, processingType, params) {
    const key = this.paramsCacheKey(serviceName, processingType)
    const value = JSON.stringify(params)

    localStorage.setItem(key, value)
  }

  findParamExpression(name) {
    const param =  _.find(this.state.parametersValues, p => p.name===name)
    return _.get(param, "expression.expression")
  }

  formRow(id, label, input) {
    return (<div key={id} className="node-row">
      <div className="node-label">{label}</div>
      <div className="node-value">{input}
      </div>
    </div>)
  }

  render() {
    const readonly = value => <input readOnly={true} type="text" className="node-input" value={value}/>
    return (
      <div>
          <div className="modalContentDye">
            <div className="node-table">
                {this.formRow("serviceName", "Service name", this.serviceList(this.services))}
                {this.formRow("processingType", "Process type", readonly(this.state.processingType))}
                {this.parametersList(this.state.nodeParameters)}
                <button type="button" className="big-blue-button input-group" onClick={e => this.invokeService()}>INVOKE SERVICE</button>
              </div>
            </div>
        <div className="queryServiceResults">
          {!_.isEmpty(this.state.queryResult.response) ?
            [
              this.prettyPrint("serviceResult", this.state.queryResult.response.result, "Service result"),
              <hr key="separator"/>,
              this.prettyPrint("collectedResults", JsonUtils.removeEmptyProperties(this.state.queryResult.response.collectedResults), "Collected results")
            ]
            : null
          }
          {this.state.queryResult.errorMessage ? <p className={"alert alert-danger"}>{this.state.queryResult.errorMessage}</p> : null}
        </div>
      </div>
    )
  }


  prettyPrint(id, json, title) {
    if (!this.hasSomeValue(json)) {
      return null
    } else {
      const toPrint = _.isObject(json) ? json : {"result": json}
      return (
        <div key={id}>
          <p>{title}</p>
          <JSONTree style={{fontSize: 25}} data={toPrint} hideRoot={true} shouldExpandNode={(key, data, level) => level < 3} theme={{
            label: {
              fontWeight: 'normal',
            },
            tree: {
              backgroundColor: 'none'
            }
          }}/>
        </div>
      )
    }
  }

  hasSomeValue = (o) => {
    //_.isEmpty(123) returns true... more: https://github.com/lodash/lodash/issues/496
    return (_.isNumber(o) || _.isBoolean(o)) || !_.isEmpty(o)
  }

}