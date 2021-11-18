import ProcessUtils from '../common/ProcessUtils'
import _ from 'lodash'

const unknown = {"type": "Unknown", refClazzName: "java.lang.Object"}

//nodeId, process, processDefinition, fieldName, processCategory  ==> (processDefinition, processCategory, process) => (nodeId, parameterDefinition)
describe("process available variables finder", () => {
  it("should find available variables with its types in process at the beginning of the process", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", process)("processVariables")
    expect(availableVariables).toEqual({
      "input": {refClazzName: "org.nussknacker.model.Transaction"},
      "date": {refClazzName:"java.time.LocalDate"}
    })
  })

  
  it("should find available variables with its types in process in the end of the process", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", process)("endEnriched")

    expect(availableVariables).toEqual({
      "input": {refClazzName:"org.nussknacker.model.Transaction"},
      "date": {refClazzName:"java.time.LocalDate"},
      "parsedTransaction": {refClazzName:"org.nussknacker.model.Transaction"},
      "aggregateResult": {refClazzName:"java.lang.String"},
      "processVariables": unknown, //fixme how to handle variableBuilder here?
      "someVariableName": unknown
    })
  })


  it("should find subprocess parameters as variables with its types", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", subprocess)("endEnriched")
    expect(availableVariables).toEqual({
      "date": {refClazzName:"java.time.LocalDate"},
      "subprocessParam": {refClazzName:"java.lang.String"}
    })
  })


  it("should return only global variables for dangling node", () => {
    const danglingNodeId = "someFilterNode"
    const newEdges = _.reject(process.edges, (edge) => {return edge.from == danglingNodeId || edge.to == danglingNodeId})
    const processWithDanglingNode = {...process, ...{edges: newEdges}}

    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", processWithDanglingNode)("danglingNodeId")

    expect(availableVariables).toEqual({
      "date": {refClazzName:"java.time.LocalDate"}
    })
  })

  it("should use variables from validation results if exist", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", processWithVariableTypes)("variableNode")

    expect(availableVariables).toEqual({
      "input": {refClazzName:"java.lang.String"}, "processVariables": {refClazzName:"java.util.Map", fields: {field1: {refClazzName: "java.lang.String"}}}
    })
  })

  it("should fallback to variables decoded from graph if typing via validation fails", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", processWithVariableTypes)("anonymousUserFilter")

    expect(availableVariables).toEqual({
      "date": {refClazzName:"java.time.LocalDate"},
      someVariableName: unknown,
      processVariables: unknown,
      input: {refClazzName: 'org.nussknacker.model.Transaction'}
    })
  })

  it("should filter globalVariables by Category3", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category3", processWithVariableTypes)("anonymousUserFilter")

    expect(availableVariables).toEqual({
      "date2": {refClazzName:"java.time.Date"},
      someVariableName: unknown,
      processVariables: unknown,
      input: {refClazzName: 'org.nussknacker.model.Transaction'}
    })
  })

  it("should not fetch globalVariables for no defined category", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, null, processWithVariableTypes)("anonymousUserFilter")

    expect(availableVariables).toEqual({
      someVariableName: unknown,
      processVariables: unknown,
      input: {refClazzName: 'org.nussknacker.model.Transaction'}
    })
  })


  it("add additional variables to node if defined", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", processWithVariableTypes)("aggregateId", paramWithAdditionalVariables)

    expect(availableVariables).toEqual({
      "additional1": {refClazzName: "java.lang.String"},
      "input": {refClazzName:"org.nussknacker.model.Transaction"},
      "date": {refClazzName:"java.time.LocalDate"},
      "parsedTransaction": {refClazzName:"org.nussknacker.model.Transaction"},
      "processVariables": unknown,
      "someVariableName": unknown
    })
  })

  it("hide variables in parameter if defined", () => {
    const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, "Category1", processWithVariableTypes)("aggregateId", paramWithVariablesToHide)

    expect(availableVariables).toEqual({})
  })
})

const paramWithAdditionalVariables = {name: "withAdditional", additionalVariables: {"additional1": { "refClazzName": "java.lang.String"}}}

const paramWithVariablesToHide = {name: "withVariablesToHide", variablesToHide: ["input", "date", "parsedTransaction", "processVariables", "someVariableName"]}

const processDefinition = {
  "services" : { "transactionParser": { "parameters": [], "returnType": { "refClazzName": "org.nussknacker.model.Transaction"}, "categories": ["Category1"]},},
  "sourceFactories" : { "kafka-transaction": { "parameters": [ { "name": "topic", "typ": { "refClazzName": "java.lang.String"} }], "returnType": { "refClazzName": "org.nussknacker.model.Transaction"}, "categories": [ "Category1" ]} },
  "sinkFactories" : { "endTransaction" : { "parameters": [ { "name": "topic", "typ": { "refClazzName": "java.lang.String"}}], "returnType" : { "refClazzName": "pl.touk.esp.engine.kafka.KafkaSinkFactory"}, "categories" : [ "Category1", "Category2", "Category3"]}},
  "customStreamTransformers" : {
    "transactionAggregator" : {
      "parameters": [
        paramWithAdditionalVariables,
        paramWithVariablesToHide
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
  "properties": { "parallelism": 2 },
  "nodes": [
    { "type": "Source", "id": "start", "ref": { "typ": "kafka-transaction", "parameters": [{ "name": "topic", "value": "transaction.topic"}]}},
    { "type": "VariableBuilder", "id": "processVariables", "varName": "processVariables", "fields": [{ "name": "processingStartTime", "expression": { "language": "spel", "expression": "#now()"}}]},
    { "type": "Variable", "id": "variableNode", "varName": "someVariableName", "value": { "language": "spel", "expression": "'value'"}},
    { "type": "Filter", "id": "anonymousUserFilter", "expression": { "language": "spel", "expression": "#input.PATH != 'Anonymous'"}},
    { "type": "Enricher", "id": "decodeHtml", "service": { "id": "transactionParser", "parameters": [{ "name": "transaction", "expression": { "language": "spel", "expression": "#input"}}]}, "output": "parsedTransaction"},
    { "type": "Filter", "id": "someFilterNode", "expression": { "language": "spel", "expression": "true"}},
    { "type": "CustomNode", "id": "aggregateId", "outputVar": "aggregateResult", "nodeType": "transactionAggregator", "parameters": [{"name": "withAdditional", "value": "''"}]},
    { "type": "Sink", "id": "endEnriched", "ref": { "typ": "transactionSink", "parameters": [{ "name": "topic", "value": "transaction.errors"}]}}
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
  "validationResult": { "errors": {"invalidNodes": {}}, nodeResults: {
    "start": {},
    "processVariables": {variableTypes: {"input": {refClazzName:"java.lang.String"}}},
    "variableNode": {variableTypes: {"input": {refClazzName:"java.lang.String"}, "processVariables": {refClazzName:"java.util.Map", fields: {field1: {refClazzName: "java.lang.String"}}}}}
    }
  }
}

const subprocess = {
  "id": "subprocess1",
  "properties": { "parallelism": 2 },
  "nodes": [
    { "type": "SubprocessInputDefinition", "id": "start", "parameters": [{ "name": "subprocessParam", "typ":{ "refClazzName": "java.lang.String"}}]},
    { "type": "Filter", "id": "filter1", "expression": { "language": "spel", "expression": "#input.PATH != 'Anonymous'"}},
    { "type": "Sink", "id": "endEnriched", "ref": { "typ": "transactionSink", "parameters": [{ "name": "topic", "value": "transaction.errors"}]}}
  ],
  "edges": [
    { "from": "start", "to": "filter1"},
    { "from": "filter1", "to": "endEnriched"}
  ],
  "validationResult": { "errors": {"invalidNodes": {}}}
}



describe("process utils", () => {
  const typingResult1 = { type: "java.lang.String", display: "String" }
  const typingResult2 = { type: "java.lang.Object", display: "Unknown" }
  it("should convert to readable type", () => {
    expect(ProcessUtils.humanReadableType(typingResult1)).toEqual("String")
    expect(ProcessUtils.humanReadableType(typingResult2)).toEqual("Unknown")
  })
})       