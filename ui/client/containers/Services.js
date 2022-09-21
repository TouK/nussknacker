import React from "react"
import {JSONTree} from "react-json-tree"
import {connect} from "react-redux"
import {withRouter} from "react-router-dom"
import ActionsUtils from "../actions/ActionsUtils"
import {removeEmptyProperties} from "../common/JsonUtils"
import ProcessUtils from "../common/ProcessUtils"
import {ExpressionLang} from "../components/graph/node-modal/editors/expression/types"
import {InputWithFocus, SelectWithFocus} from "../components/withFocus"
import HttpService from "../http/HttpService"
import "../stylesheets/processes.styl"
import "../stylesheets/graph.styl"
import {find, flatMap, get, isBoolean, isEmpty, isNumber, isObject, map, set, sortBy} from "lodash"

class Services extends React.Component {

  jsonTreeTheme = {
    label: {
      fontWeight: "normal",
    },
    tree: {
      backgroundColor: "none",
    },
  }

  constructor(props) {
    super(props)
    this.state = {
      services: [],
      processingType: "",
      serviceName: "",
      nodeParameters: {},
      parametersValues: [],
      queryResult: {
        response: {},
        errorMessage: null,
      },
    }
  }

  componentDidMount() {
    HttpService.fetchServices().then(response => {
      this.setState({services: mapProcessDefinitionToServices(response.data)})
    })
  }

  setService(idx) {
    const service = this.state.services[idx]
    const cachedParams = this.cachedServiceParams(service.name, service.processingType)

    const initializeParameter = paramName => find(cachedParams, cp => cp.name === paramName) || {
      name: paramName,
      expression: {
        //TODO: is it always fixed?
        language: ExpressionLang.SpEL,
        expression: "",
      },
    }

    const initializeParametersValues = params => map(params, p => initializeParameter(p.name))
    this.setState(
      {
        processingType: service.processingType,
        serviceName: service.name,
        nodeParameters: service.parameters,
        parametersValues: initializeParametersValues(service.parameters || []),
        queryResult: {
          response: {},
          errorMessage: null,
        },
      }
    )
  }

  serviceList() {
    return (
      <SelectWithFocus className="node-input" onChange={e => this.setService(e.target.value)}>
        {this.state.services.map((service, idx) => <option key={idx} value={idx}>{service.name}</option>)}
      </SelectWithFocus>
    )
  }

  //TODO: use NodeDetailsContent (after NDC refactor)
  parametersList(params) {
    const setParam = paramName => value => {
      const params = this.state.parametersValues
      set(find(params, p => p.name === paramName), "expression.expression", value)
      this.setState({parametersValues: params})
    }

    return (
      <span>
        {map(params, (param) => this.formRow(
          `param_${param.name}`,
          <span>{param.name}
            <div className="labelFooter">{ProcessUtils.humanReadableType(param.typ)}</div></span>,
          <span>
            <InputWithFocus
              className="node-input"
              value={this.findParamExpression(param.name)}
              onChange={e => setParam(param.name)(e.target.value)}
            />
          </span>,
        ))}
      </span>
    )
  }

  invokeService() {
    HttpService.invokeService(
      this.state.processingType,
      this.state.serviceName,
      this.state.parametersValues,
    ).then(response => {
      this.cacheServiceParams(this.state.serviceName, this.state.processingType, this.state.parametersValues)
      this.setState({queryResult: {response: response.data, errorMessage: null}})
    }).catch(error => {
      this.setState({queryResult: {response: {}, errorMessage: get(error, "response.data.message")}})
    })
  }

  paramsCacheKey(serviceName, processingType) {
    return `${serviceName}:${processingType}:parameters`
  }

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
    const param = find(this.state.parametersValues, p => p.name === name)
    return get(param, "expression.expression")
  }

  formRow(id, label, input) {
    return (
      <div key={id} className="node-row">
        <div className="node-label">{label}</div>
        <div className="node-value">{input}</div>
      </div>
    )
  }

  render() {
    const readonly = value => <InputWithFocus readOnly={true} type="text" className="node-input" value={value}/>
    return (
      <div className="services">
        <div className="modalContentDye">
          <div className="node-table">
            {this.formRow("serviceName", "Service name", this.serviceList(this.state.services))}
            {this.formRow("processingType", "Process type", readonly(this.state.processingType))}
            {this.parametersList(this.state.nodeParameters)}
            <button type="button" className="big-blue-button input-group" onClick={e => this.invokeService()}>INVOKE
              SERVICE
            </button>
          </div>
        </div>
        <div className="queryServiceResults">
          {
            !isEmpty(this.state.queryResult.response) ?
              [
                this.prettyPrint("serviceResult", this.state.queryResult.response.result, "Service result"),
                <hr key="separator"/>,
                this.prettyPrint("collectedResults", removeEmptyProperties(this.state.queryResult.response.collectedResults), "Collected results"),
              ] :
              null
          }
          {this.state.queryResult.errorMessage ?
            <p className={"alert alert-danger"}>{this.state.queryResult.errorMessage}</p> :
            null}
        </div>
      </div>
    )
  }

  prettyPrint(id, json, title) {
    if (this.hasSomeValue(json)) {
      const data = isObject(json) ? json : {result: json}

      return (
        <div key={id}>
          <p>{title}</p>
          <JSONTree
            style={{fontSize: 25}}
            data={data}
            hideRoot={true}
            shouldExpandNode={(key, data, level) => level < 3}
            theme={this.jsonTreeTheme}
          />
        </div>
      )
    }

    return null
  }

  hasSomeValue = (o) => {
    //isEmpty(123) returns true... more: https://github.com/lodash/lodash/issues/496
    return isNumber(o) || isBoolean(o) || !isEmpty(o)
  }
}

Services.header = "Services"
Services.key = "services"

export function mapProcessDefinitionToServices(services) {
  return sortBy(
    flatMap(services, (typeServices, processingType) => map(typeServices, (service, name) => ({
      name: name,
      categories: service.categories,
      parameters: service.parameters,
      processingType: processingType,
    }))), s => s.name,
  )
}

const mapState = state => ({loggedUser: state.settings.loggedUser})
export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Services))
