import ProcessUtils from '../common/ProcessUtils'
import _ from 'lodash'

describe("process available variables finder", () => {
  it("should find available variables with its types in process at the beginning of the process", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("processVariables", process, processDefinition, null, "Category1")
    expect(availableVariables).toEqual({
      "input": {refClazzName: "org.nussknacker.model.Transaction"},
      "date": {refClazzName:"java.time.LocalDate"}
    })
  })

  
  it("should find available variables with its types in process in the end of the process", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("endEnriched", process, processDefinition, null, "Category1")
    expect(availableVariables).toEqual({
      "input": {refClazzName:"org.nussknacker.model.Transaction"},
      "date": {refClazzName:"java.time.LocalDate"},
      "parsedTransaction": {refClazzName:"org.nussknacker.model.Transaction"},
      "aggregateResult": {refClazzName:"java.lang.String"},
      "processVariables": {refClazzName:"java.lang.Object"}, //fixme how to handle variableBuilder here?
      "someVariableName": {refClazzName:"java.lang.Object"}
    })
  })


  it("should find subprocess parameters as variables with its types", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("endEnriched", subprocess, processDefinition, null, "Category1")
    expect(availableVariables).toEqual({
      "date": {refClazzName:"java.time.LocalDate"},
      "subprocessParam": {refClazzName:"java.lang.String"}
    })
  })


  it("should return only global variables for dangling node", () => {
    const danglingNodeId = "someFilterNode"
    const newEdges = _.reject(process.edges, (edge) => {return edge.from == danglingNodeId || edge.to == danglingNodeId})
    const processWithDanglingNode = {...process, ...{edges: newEdges}}

    const availableVariables = ProcessUtils.findAvailableVariables(danglingNodeId, processWithDanglingNode, processDefinition, null, "Category1")

    expect(availableVariables).toEqual({
      "date": {refClazzName:"java.time.LocalDate"}
    })
  })

  it("should use variables from validation results if exist", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("variableNode", processWithVariableTypes, processDefinition, null, "Category1")

    expect(availableVariables).toEqual({
      "input": {refClazzName:"java.lang.String"}, "processVariables": {refClazzName:"java.util.Map", fields: {field1: {refClazzName: "java.lang.String"}}}
    })
  })

  it("should fallback to variables decoded from graph if typing via validation fails", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("anonymousUserFilter", processWithVariableTypes, processDefinition, null, "Category1")

    expect(availableVariables).toEqual({
      "date": {refClazzName:"java.time.LocalDate"},
      someVariableName: {refClazzName:"java.lang.Object"},
      processVariables: {refClazzName:"java.lang.Object"},
      input: {refClazzName: 'org.nussknacker.model.Transaction'}
    })
  })

  it("should filter globalVariables by Category3", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("anonymousUserFilter", processWithVariableTypes, processDefinition, null, "Category3")

    expect(availableVariables).toEqual({
      "date2": {refClazzName:"java.time.Date"},
      someVariableName: {refClazzName:"java.lang.Object"},
      processVariables: {refClazzName:"java.lang.Object"},
      input: {refClazzName: 'org.nussknacker.model.Transaction'}
    })
  })

  it("should not fetch globalVariables for no defined category", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("anonymousUserFilter", processWithVariableTypes, processDefinition)

    expect(availableVariables).toEqual({
      someVariableName: {refClazzName:"java.lang.Object"},
      processVariables: {refClazzName:"java.lang.Object"},
      input: {refClazzName: 'org.nussknacker.model.Transaction'}
    })
  })

  it("add additional variables to node if defined", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("aggregateId", processWithVariableTypes, processDefinition, "withAdditional", "Category1")
    expect(availableVariables).toEqual({
      "additional1": {refClazzName: "java.lang.String"},
      "input": {refClazzName:"org.nussknacker.model.Transaction"},
      "date": {refClazzName:"java.time.LocalDate"},
      "parsedTransaction": {refClazzName:"org.nussknacker.model.Transaction"},
      "processVariables": {refClazzName:"java.lang.Object"}, 
      "someVariableName": {refClazzName:"java.lang.Object"}
    })
  })
})

const processDefinition = {
  "services" : { "transactionParser": { "parameters": [], "returnType": { "refClazzName": "org.nussknacker.model.Transaction"}, "categories": ["Category1"]},},
  "sourceFactories" : { "kafka-transaction": { "parameters": [ { "name": "topic", "typ": { "refClazzName": "java.lang.String"} }], "returnType": { "refClazzName": "org.nussknacker.model.Transaction"}, "categories": [ "Category1" ]} },
  "sinkFactories" : { "endTransaction" : { "parameters": [ { "name": "topic", "typ": { "refClazzName": "java.lang.String"}}], "returnType" : { "refClazzName": "pl.touk.esp.engine.kafka.KafkaSinkFactory"}, "categories" : [ "Category1", "Category2", "Category3"]}},
  "customStreamTransformers" : {
    "transactionAggregator" : {
      "parameters": [
        {name: "withAdditional", additionalVariables: {"additional1": { "refClazzName": "java.lang.String"}}}
      ],
      "returnType": {"refClazzName": "java.lang.String"}, "categories": [ "Category12"]}},
  "exceptionHandlerFactory" : { "parameters" : [ { "name": "errorsTopic", "typ": { "refClazzName": "java.lang.String"}}], "returnType" : { "refClazzName": "org.nussknacker.process.espExceptionHandlerFactory"}, "categories" : []},
  "globalVariables" : {
    "date": { "returnType": { "refClazzName": "java.time.LocalDate"}, "categories" : [ "Category1", "Category2"]},
    "wrong1": { "returnType": null, "categories" : [ "Category1", "Category2"]},
    "date2": { "returnType": { "refClazzName": "java.time.Date"}, "categories" : [ "Category3"]},
    "date3": { "returnType": { "refClazzName": "java.time.Date"}, "categories" : []}
  },
  "typesInformation" : [
    { "clazzName": { "refClazzName": "org.nussknacker.model.Transaction"}, "methods": { "CUSTOMER_ID": { "refClazz" : {"refClazzName": "java.lang.String"}}}},
    { "clazzName": { "refClazzName": "pl.touk.nussknacker.model.Account"}, "methods": { "ACCOUNT_NO": { "refClazz" : { "refClazzName": "java.lang.String"}}}},
    { "clazzName": { "refClazzName": "java.time.LocalDate"}, "methods": { "atStartOfDay": { "refClazz" : { "refClazzName": "java.time.ZonedDateTime"}}}}
  ]
}


const process = {
  "id": "transactionStart",
  "properties": { "parallelism": 2, "exceptionHandler": { "parameters": [{ "name": "errorsTopic", "value": "transaction.errors"}]}},
  "nodes": [
    { "type": "Source", "id": "start", "ref": { "typ": "kafka-transaction", "parameters": [{ "name": "topic", "value": "transaction.topic"}]}},
    { "type": "VariableBuilder", "id": "processVariables", "varName": "processVariables", "fields": [{ "name": "processingStartTime", "expression": { "language": "spel", "expression": "#now()"}}]},
    { "type": "Variable", "id": "variableNode", "varName": "someVariableName", "value": { "language": "spel", "expression": "'value'"}},
    { "type": "Filter", "id": "anonymousUserFilter", "expression": { "language": "spel", "expression": "#input.PATH != 'Anonymous'"}},
    { "type": "Enricher", "id": "decodeHtml", "service": { "id": "transactionParser", "parameters": [{ "name": "transaction", "expression": { "language": "spel", "expression": "#input"}}]}, "output": "parsedTransaction"},
    { "type": "Filter", "id": "someFilterNode", "expression": { "language": "spel", "expression": "true"}},
    { "type": "CustomNode", "id": "aggregateId", "outputVar": "aggregateResult", "nodeType": "transactionAggregator", "parameters": [{"name": "withAdditional", "value": "''"}]},
    { "type": "Sink", "id": "endEnriched", "ref": { "typ": "transactionSink", "parameters": [{ "name": "topic", "value": "transaction.errors"}]}, "endResult": { "language": "spel", "expression": "#finalTransaction.toJson()"}}
  ],
  "edges": [
    { "from": "start", "to": "processVariables"},
    { "from": "processVariables", "to": "variableNode"},
    { "from": "variableNode", "to": "anonymousUserFilter"},
    { "from": "anonymousUserFilter", "to": "decodeHtml"},
    { "from": "decodeHtml", "to": "someFilterNode"},
    { "from": "someFilterNode", "to": "aggregateId"},
    { "from": "aggregateId", "to": "endEnriched"}
  ],
  "validationResult": { "errors": {"invalidNodes": {}}}
}

const processWithVariableTypes = {
  ...process,
  "validationResult": { "errors": {"invalidNodes": {}}, variableTypes: {
      "start": {},
      "processVariables": {"input": {refClazzName:"java.lang.String"}},
      "variableNode": {"input": {refClazzName:"java.lang.String"}, "processVariables": {refClazzName:"java.util.Map", fields: {field1: {refClazzName: "java.lang.String"}}}}
    }
  }
}

const subprocess = {
  "id": "subprocess1",
  "properties": { "parallelism": 2, "exceptionHandler": { "parameters": [{ "name": "errorsTopic", "value": "transaction.errors"}]}},
  "nodes": [
    { "type": "SubprocessInputDefinition", "id": "start", "parameters": [{ "name": "subprocessParam", "typ":{ "refClazzName": "java.lang.String"}}]},
    { "type": "Filter", "id": "filter1", "expression": { "language": "spel", "expression": "#input.PATH != 'Anonymous'"}},
    { "type": "Sink", "id": "endEnriched", "ref": { "typ": "transactionSink", "parameters": [{ "name": "topic", "value": "transaction.errors"}]}, "endResult": { "language": "spel", "expression": "#finalTransaction.toJson()"}}
  ],
  "edges": [
    { "from": "start", "to": "filter1"},
    { "from": "filter1", "to": "endEnriched"}
  ],
  "validationResult": { "errors": {"invalidNodes": {}}}
}



describe("process utils", () => {
  it("should convert to readable type", () => {
    expect(ProcessUtils.humanReadableType("java.lang.String")).toEqual("String")
    expect(ProcessUtils.humanReadableType("int")).toEqual("Int")
  })
})       