import ace from "ace-builds/src-noconflict/ace"
import cn from "classnames"
import {isEmpty, isEqual, map, overEvery} from "lodash"
import PropTypes from "prop-types"
import React from "react"
import ReactDOMServer from "react-dom/server"
import {connect} from "react-redux"
import ActionsUtils from "../../../../../actions/ActionsUtils"
import ProcessUtils from "../../../../../common/ProcessUtils"
import HttpService from "../../../../../http/HttpService"
import ValidationLabels from "../../../../modals/ValidationLabels"
import {allValid} from "../Validators"
import AceEditor from "./AceWithSettings"
import ExpressionSuggester from "./ExpressionSuggester"

const {TokenIterator} = ace.require("ace/token_iterator")

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

var inputExprIdCounter = 0

const identifierRegexpsWithoutDot = [/[#a-zA-Z0-9-_]/]
const identifierRegexpsIncludingDot = [/[#a-zA-Z0-9-_.]/]

function isSqlTokenAllowed(iterator, modeId) {
  if (modeId === "ace/mode/sql") {
    let token = iterator.getCurrentToken()
    while (token && (token.type !== "spel.start" && token.type !== "spel.end")) {
      token = iterator.stepBackward()
    }
    return token?.type === "spel.start"
  }
  return true
}

function isSpelTokenAllowed(iterator, modeId) {
  if (modeId === "ace/mode/spel") {
    const token = iterator.getCurrentToken()
    return token?.type !== "string"
  }
  return true
}

class ExpressionSuggest extends React.Component {

  static propTypes = {
    inputProps: PropTypes.object.isRequired,
    validators: PropTypes.array,
    showValidation: PropTypes.bool,
    processingType: PropTypes.string,
    isMarked: PropTypes.bool,
    variableTypes: PropTypes.object,
    validationLabelInfo: PropTypes.string,
  }

  customAceEditorCompleter = {
    isTokenAllowed: overEvery([isSqlTokenAllowed, isSpelTokenAllowed]),
    getCompletions: (editor, session, caretPosition2d, prefix, callback) => {
      const iterator = new TokenIterator(session, caretPosition2d.row, caretPosition2d.column)
      if (!this.customAceEditorCompleter.isTokenAllowed(iterator, session.$modeId)) {
        callback()
      }

      this.expressionSuggester.suggestionsFor(this.state.value, caretPosition2d).then(suggestions => {
        // This trick enforce autocompletion to invoke getCompletions even if some result found before - in case if list of suggestions will change during typing
        editor.completer.activated = false
        // We have dot in identifier pattern to enable live autocompletion after dots, but also we remove it from pattern just before callback, because
        // otherwise our results lists will be filtered out (because entries not matches '#full.property.path' but only 'path')
        this.customAceEditorCompleter.identifierRegexps = identifierRegexpsWithoutDot
        try {
          callback(null, map(suggestions, (s) => {
            const methodName = s.methodName
            const returnType = ProcessUtils.humanReadableType(s.refClazz)
            return {
              name: methodName,
              value: methodName,
              score: 1,
              meta: returnType,
              description: s.description,
              parameters: s.parameters,
              returnType: returnType,
            }
          }))
        } finally {
          this.customAceEditorCompleter.identifierRegexps = identifierRegexpsIncludingDot
        }
      })
    },
    // We adds hash to identifier pattern to start suggestions just after hash is typed
    identifierRegexps: identifierRegexpsIncludingDot,
    getDocTooltip: (item) => {
      if (item.description || !isEmpty(item.parameters)) {
        const paramsSignature = item.parameters.map(p => `${ProcessUtils.humanReadableType(p.refClazz)} ${p.name}`).join(", ")
        const javaStyleSignature = `${item.returnType} ${item.name}(${paramsSignature})`
        item.docHTML = ReactDOMServer.renderToStaticMarkup((
          <div className="function-docs">
            <b>{javaStyleSignature}</b>
            <hr/>
            <p>{item.description}</p>
          </div>
        ))
      }
    },
  }

  constructor(props) {
    super(props)
    inputExprIdCounter += 1
    this.state = {
      value: props.inputProps.value,
      id: `inputExpr${inputExprIdCounter}`,
    }
    this.expressionSuggester = this.createExpressionSuggester(props)
  }

  //fixme is this enough?
  //this shouldComponentUpdate is for cases when there are multiple instances of suggestion component in one view and to make them not interfere with each other
  //fixme maybe use this.state.id here?
  shouldComponentUpdate(nextProps, nextState) {
    return !isEqual(this.state.value, nextState.value) ||
      !isEqual(this.state.editorFocused, nextState.editorFocused) ||
      !isEqual(this.props.validators, nextProps.validators) ||
      !isEqual(this.props.variableTypes, nextProps.variableTypes) ||
      !isEqual(this.props.validationLabelInfo, nextProps.validationLabelInfo)

  }

  componentDidUpdate(prevProps, prevState) {
    this.expressionSuggester = this.createExpressionSuggester(this.props)
    if (!isEqual(this.state.value, prevState.value)) {
      this.props.inputProps.onValueChange(this.state.value)
    }
  }

  createExpressionSuggester = (props) => {
    return new ExpressionSuggester(props.typesInformation, props.variableTypes, props.processingType, HttpService)
  }

  onChange = (newValue) => {
    this.setState({
      value: newValue,
    })
  }

  render() {
    if (this.props.dataResolved) {
      const {isMarked, showValidation, inputProps, validators} = this.props
      const {editorFocused, value} = this.state

      return (
        <React.Fragment>
          <div className={cn([
            "row-ace-editor",
            showValidation && !allValid(validators, [value]) && "node-input-with-error",
            isMarked && "marked",
            editorFocused && "focused",
            inputProps.readOnly && "read-only",
          ])}
          >
            <AceEditor
              value={value}
              onChange={this.onChange}
              onFocus={this.setEditorFocus(true)}
              onBlur={this.setEditorFocus(false)}
              inputProps={inputProps}
              customAceEditorCompleter={this.customAceEditorCompleter}
            />
          </div>
          {showValidation &&
          <ValidationLabels validators={validators} values={[value]} validationLabelInfo={this.props.validationLabelInfo}/>}
        </React.Fragment>
      )
    } else {
      return null
    }

  }

  setEditorFocus = (focus) => () => this.setState({editorFocused: focus})
}

function mapState(state) {
  const processDefinitionData = !isEmpty(state.settings.processDefinitionData) ? state.settings.processDefinitionData : {processDefinition: {typesInformation: []}}
  const dataResolved = !isEmpty(state.settings.processDefinitionData)
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  return {
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    processingType: state.graphReducer.processToDisplay.processingType,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest)
