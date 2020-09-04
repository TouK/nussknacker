import cn from "classnames"
import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import ReactDOMServer from "react-dom/server"
import {connect} from "react-redux"
import ActionsUtils from "../../../../../actions/ActionsUtils"
import ProcessUtils from "../../../../../common/ProcessUtils"
import HttpService from "../../../../../http/HttpService"
import ValidationLabels from "../../../../modals/ValidationLabels"

import AceEditor from "./ace"
import ExpressionSuggester from "./ExpressionSuggester"
import {allValid} from "../Validators"
import {reducer as nodeDetails} from "../../../../../reducers/nodeDetailsState"

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

var inputExprIdCounter = 0

const identifierRegexpsWithoutDot = [/[#a-z0-9-_]/]
const identifierRegexpsIncludingDot = [/[#a-z0-9-_.]/]

const commandFindConfiguration = {
  name: "find",
  bindKey: {win: "Ctrl-F", mac: "Command-F"},
  exec: () => false,
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
    getCompletions: (editor, session, caretPosition2d, prefix, callback) => {
      this.expressionSuggester.suggestionsFor(this.state.value, caretPosition2d).then(suggestions => {
        // This trick enforce autocompletion to invoke getCompletions even if some result found before - in case if list of suggestions will change during typing
        editor.completer.activated = false
        // We have dot in identifier pattern to enable live autocompletion after dots, but also we remove it from pattern just before callback, because
        // otherwise our results lists will be filtered out (because entries not matches '#full.property.path' but only 'path')
        this.customAceEditorCompleter.identifierRegexps = identifierRegexpsWithoutDot
        try {
          callback(null, _.map(suggestions, (s) => {
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
      if (item.description || !_.isEmpty(item.parameters)) {
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
    return !_.isEqual(this.state.value, nextState.value) ||
        !_.isEqual(this.state.editorFocused, nextState.editorFocused) ||
        !_.isEqual(this.props.validators, nextProps.validators) ||
        !_.isEqual(this.props.variableTypes, nextProps.variableTypes)
  }

  componentDidUpdate(prevProps, prevState) {
    this.expressionSuggester = this.createExpressionSuggester(this.props)
    if (!_.isEqual(this.state.value, prevState.value)) {
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
      const THEME = "nussknacker"

      //monospace font seems to be mandatory to make ace cursor work well,
      const FONT_FAMILY = "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace"

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
              mode={inputProps.language}
              width={"100%"}
              minLines={1}
              maxLines={50}
              theme={THEME}
              onChange={this.onChange}
              value={value}
              showPrintMargin={false}
              cursorStart={-1} //line start
              showGutter={false}
              highlightActiveLine={false}
              highlightGutterLine={false}
              wrapEnabled={true}
              editorProps={{
                // eslint-disable-next-line i18next/no-literal-string
                $blockScrolling: "Infinity",
              }}
              className={inputProps.readOnly ? " read-only" : ""}
              setOptions={{
                indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
                enableBasicAutocompletion: [this.customAceEditorCompleter],
                enableLiveAutocompletion: true,
                enableSnippets: false,
                showLineNumbers: false,
                fontSize: 16,
                fontFamily: FONT_FAMILY,
                readOnly: inputProps.readOnly,
              }}
              commands={[commandFindConfiguration]}
              onFocus={this.setEditorFocus(true)}
              onBlur={this.setEditorFocus(false)}
            />
          </div>
          {showValidation && <ValidationLabels validators={validators} values={[value]} validationLabelInfo={this.props.validationLabelInfo}/>}
        </React.Fragment>
      )
    } else {
      return null
    }

  }

  setEditorFocus = (focus) => () => this.setState({editorFocused: focus})
}

function mapState(state) {
  const processDefinitionData = !_.isEmpty(state.settings.processDefinitionData) ? state.settings.processDefinitionData : {processDefinition: {typesInformation: []}}
  const dataResolved = !_.isEmpty(state.settings.processDefinitionData)
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  return {
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    processingType: state.graphReducer.processToDisplay.processingType,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest)
