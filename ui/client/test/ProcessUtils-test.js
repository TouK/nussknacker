import ProcessUtils from '../common/ProcessUtils'
import _ from 'lodash'

describe("process available variables finder", () => {
  it("should find available variables with its types in process at the beginning of the process", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("processVariables", process, processDefinition)
    expect(availableVariables).toEqual({
      "#input": "org.esp.esp.model.Transaction",
      "#date": "java.time.LocalDate"
    })
  })

  it("should find available variables with its types in process in the end of the process", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("endEnriched", process, processDefinition)
    expect(availableVariables).toEqual({
      "#input": "org.esp.esp.model.Transaction",
      "#date": "java.time.LocalDate",
      "#parsedTransaction": "org.esp.esp.model.Transaction",
      "#aggregateResult": "java.lang.String",
      "#processVariables": "java.lang.Object", //fixme how to handle variableBuilder here?
      "#someVariableName": "java.lang.Object"
    })
  })


  it("should find subprocess parameters as variables with its types", () => {
    const availableVariables = ProcessUtils.findAvailableVariables("endEnriched", subprocess, processDefinition)
    expect(availableVariables).toEqual({
      "#date": "java.time.LocalDate",
      "#subprocessParam": "java.lang.String"
    })
  })


  it("should return only global variables for dangling node", () => {
    const danglingNodeId = "someFilterNode"
    const newEdges = _.reject(process.edges, (edge) => {return edge.from == danglingNodeId || edge.to == danglingNodeId})
    const processWithDanglingNode = {process, ...{edges: newEdges}}

    const availableVariables = ProcessUtils.findAvailableVariables(danglingNodeId, processWithDanglingNode, processDefinition)

    expect(availableVariables).toEqual({
      "#date": "java.time.LocalDate"
    })
  })

})

const processDefinition = {
  "services" : { "transactionParser": { "parameters": [], "returnType": { "refClazzName": "org.esp.esp.model.Transaction"}, "categories": ["Category11"]},},
  "sourceFactories" : { "kafka-transaction": { "parameters": [ { "name": "topic", "typ": { "refClazzName": "java.lang.String"} }], "returnType": { "refClazzName": "org.esp.esp.model.Transaction"}, "categories": [ "Category11" ]} },
  "sinkFactories" : { "endTransaction" : { "parameters": [ { "name": "topic", "typ": { "refClazzName": "java.lang.String"}}], "returnType" : { "refClazzName": "pl.touk.esp.engine.kafka.KafkaSinkFactory"}, "categories" : [ "Category12", "Category11", "Category1"]}},
  "customStreamTransformers" : { "transactionAggregator" : { "parameters": [], "returnType": { "refClazzName": "java.lang.String"}, "categories": [ "Category12"]}},
  "exceptionHandlerFactory" : { "parameters" : [ { "name": "errorsTopic", "typ": { "refClazzName": "java.lang.String"}}], "returnType" : { "refClazzName": "org.esp.esp.process.espExceptionHandlerFactory"}, "categories" : []},
  "globalVariables" : { "date": { "returnType": { "refClazzName": "java.time.LocalDate"}, "categories" : [ "Category12", "Category11"]}},
  "typesInformation" : [
    { "clazzName": { "refClazzName": "org.esp.esp.model.Transaction"}, "methods": { "CUSTOMER_ID": { "refClazzName": "java.lang.String"}}},
    { "clazzName": { "refClazzName": "pl.touk.esp.model.Account"}, "methods": { "ACCOUNT_NO": { "refClazzName": "java.lang.String"}}},
    { "clazzName": { "refClazzName": "java.time.LocalDate"}, "methods": { "atStartOfDay": { "refClazzName": "java.time.ZonedDateTime"}}}
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
    { "type": "CustomNode", "id": "aggregateId", "outputVar": "aggregateResult", "nodeType": "transactionAggregator", "parameters": []},
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