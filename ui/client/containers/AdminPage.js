import React from 'react'
import { connect } from 'react-redux';
import _ from "lodash";
import {Table, Thead, Th, Tr, Td} from "reactable";
import { Tab, Tabs, TabList, TabPanel } from 'react-tabs';
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import ProcessUtils from "../common/ProcessUtils";
import * as JsonUtils from '../common/JsonUtils';
import JSONTree from 'react-json-tree'
import * as VisualizationUrl from '../common/VisualizationUrl'

import 'react-tabs/style/react-tabs.css';
import filterIcon from '../assets/img/search.svg'
import CustomProcesses from "./CustomProcesses";

class AdminPage extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      processes: [],
      componentIds: [],
      unusedComponents: [],
      services:{}
    }
  }

  componentDidMount() {
    HttpService.fetchProcessesDetails().then ((processes) => {
      HttpService.fetchSubProcessesDetails().then((subProcesses) =>{
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
    Promise.all([
        HttpService.fetchProcessDefinitionData('streaming', false, {}),
        HttpService.fetchProcessDefinitionData('request-response', false, {})
    ]).catch(e=>{throw e})
        .then((values) => {
            this.setState({
                services: {
                    streaming: values[0].processDefinition.services,
                    'request-response': values[1].processDefinition.services
                }
            })
        })
  }

  render() {
    const tabs = [
      {tabName: "Search components", component: <ProcessSearch componentIds={this.state.componentIds} processes={this.state.processes}/>},
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
      componentToFind: null,
      filterVal: ''
    }
  }

  render() {
    const found = _.map(ProcessUtils.search(this.props.processes, this.state.componentToFind), (result) => {
      return {
        process: result.process.id,
        node: result.node.id,
        category: result.process.processCategory
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
          <Table className="esp-table"
                 sortable={['process', 'node', 'category']}
                 filterable={['process', 'node', 'category']}
                 noDataText="No matching records found."
                 hideFilterInput
                 filterBy={this.state.filterVal.toLowerCase()}
          >
            <Thead>
              <Th column="process">Process</Th>
              <Th column="node">Node</Th>
              <Th column="category">Category</Th>
            </Thead>
            {found.map(row => {
              return (
                <Tr>
                  <Td column="process">{row.process}</Td>
                  <Td column="node">
                    {/* TODO this url won't work for nodes used in subprocesses */}
                    <a target="_blank" href={VisualizationUrl.visualizationUrl(row.process, row.node)}>
                      {row.node}
                    </a>
                    </Td>
                  <Td column="category">{row.category}</Td>
                </Tr>
              )
            })}

            </Table> : null
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
      return {ComponentId: componentId}
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
    this.setService(this.services[0].name)
  }

  setService(serviceName){
    const initializeParametersValues = params =>
      _.map(params, p => (
        {
          "name": p.name,
          "expression": {
            "language": "spel",
            "expression": ""
          }
        }
      ));

    const service = _.find(this.services, s=>s.name===serviceName)

    this.setState(
      {
        processingType: service.processingType,
        serviceName: serviceName,
        nodeParameters: service.parameters,
        parametersValues: this.cachedServiceParams(serviceName, service.processingType) || initializeParametersValues(service.parameters||[]),
        queryResult: {
          response: {},
          errorMessage: null
        }
      })
  }

  serviceList() {
    return (
        <select className="node-input" onChange={e => this.setService(e.target.value)}>
          {this.services.map((service) =>
            <option key={service.name}>{service.name}</option>)}
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
    return _.find(this.state.parametersValues, p => p.name===name).expression.expression
  }

  formRow(label, input) {
    return (<div className="node-row">
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
                {this.formRow("service name", this.serviceList(this.services))}
                {this.formRow("processing type", readonly(this.state.processingType))}
                {this.parametersList(this.state.nodeParameters)}
                <button type="button" className="big-blue-button input-group" onClick={e => this.invokeService()}>INVOKE SERVICE</button>
              </div>
            </div>
        <div className="queryServiceResults">
          {!_.isEmpty(this.state.queryResult.response) ?
            [
              this.prettyPrint(this.state.queryResult.response.result, "Service result"),
              <hr/>,
              this.prettyPrint(JsonUtils.removeEmptyProperties(this.state.queryResult.response.collectedResults), "Collected results")
            ]
            : null
          }
          {this.state.queryResult.errorMessage ? <p className={"alert alert-danger"}>{this.state.queryResult.errorMessage}</p> : null}
        </div>
      </div>
    )
  }


  prettyPrint(json, title) {
    if (!this.hasSomeValue(json)) {
      return null
    } else {
      const toPrint = _.isObject(json) ? json : {"result": json}
      return (
        <div>
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