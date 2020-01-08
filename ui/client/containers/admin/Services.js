import React from "react"
import {withRouter} from "react-router-dom"
import "../../stylesheets/processes.styl"
import {connect} from "react-redux"
import JSONTree from "react-json-tree"
import ActionsUtils from "../../actions/ActionsUtils"
import ProcessUtils from "../../common/ProcessUtils"
import HttpService from "../../http/HttpService"
import * as JsonUtils from "../../common/JsonUtils"
import BaseAdminTab from "./BaseAdminTab"

class Services extends BaseAdminTab {

  jsonTreeTheme = {
    label: {
      fontWeight: "normal",
    },
    tree: {
      backgroundColor: "none"
    }
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
        errorMessage: null
      }
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

    const initializeParameter = paramName => _.find(cachedParams, cp => cp.name === paramName) || {
      name: paramName,
      expression: {
        //TODO: is it always fixed?
        language: "spel",
        expression: ""
      }
    }

    const initializeParametersValues = params => _.map(params, p => initializeParameter(p.name))
    this.setState(
      {
        processingType: service.processingType,
        serviceName: service.name,
        nodeParameters: service.parameters,
        parametersValues: initializeParametersValues(service.parameters || []),
        queryResult: {
          response: {},
          errorMessage: null
        }
      })
  }

  serviceList() {
    return (
      <select className="node-input" onChange={e => this.setService(e.target.value)}>
        {this.state.services.map((service, idx) => <option key={idx} value={idx}>{service.name}</option>)}
      </select>
    )
  }

  //TODO: use NodeDetailsContent (after NDC refactor)
  parametersList(params) {
    const setParam = paramName => value => {
      const params = this.state.parametersValues
      _.set(_.find(params, p => p.name === paramName), "expression.expression", value)
      this.setState({parametersValues: params})
    }

    return (
      <span>
        {_.map(params, (param) =>
          this.formRow(
            `param_${  param.name}`,
            <span>{param.name}
              <div className="labelFooter">{ProcessUtils.humanReadableType(param.refClazzName)}</div></span>,
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
    HttpService.invokeService(
      this.state.processingType,
      this.state.serviceName,
      this.state.parametersValues
    ).then(response => {
      this.cacheServiceParams(this.state.serviceName, this.state.processingType, this.state.parametersValues)
      this.setState({queryResult: {response: response.data, errorMessage: null}})
    }).catch(error => {
      this.setState({queryResult: {response: {}, errorMessage: _.get(error, "response.data.message")}})
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
    const param = _.find(this.state.parametersValues, p => p.name === name)
    return _.get(param, "expression.expression")
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
    const readonly = value => <input readOnly={true} type="text" className="node-input" value={value}/>
    return (
      <div>
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
            !_.isEmpty(this.state.queryResult.response) ? [
              this.prettyPrint("serviceResult", this.state.queryResult.response.result, "Service result"),
              <hr key="separator"/>,
              this.prettyPrint("collectedResults", JsonUtils.removeEmptyProperties(this.state.queryResult.response.collectedResults), "Collected results")
            ] : null
          }
          {this.state.queryResult.errorMessage ?
            <p className={"alert alert-danger"}>{this.state.queryResult.errorMessage}</p> : null}
        </div>
      </div>
    )
  }

  prettyPrint(id, json, title) {
    if (this.hasSomeValue(json)) {
      const data = _.isObject(json) ? json : {result: json}

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
    //_.isEmpty(123) returns true... more: https://github.com/lodash/lodash/issues/496
    return (_.isNumber(o) || _.isBoolean(o)) || !_.isEmpty(o)
  }
}

Services.header = "Services"
Services.key = "services"

export function mapProcessDefinitionToServices(services) {
  return _.sortBy(
    _.flatMap(services, (typeServices, processingType) =>
      _.map(typeServices, (service, name) => ({
          name: name,
          categories: service.categories,
          parameters: _.map(service.parameters, p => ({
            name: p.name,
            refClazzName: p.typ.refClazzName
          })),
          returnClassName: service.returnType == null ? null : service.returnType.refClazzName,
          processingType: processingType
        })
      )
    ), s => s.name
  )
}

const mapState = state => ({loggedUser: state.settings.loggedUser})
export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Services))