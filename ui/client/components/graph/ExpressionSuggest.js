import React from "react";
import PropTypes from 'prop-types';
import ReactDOMServer from 'react-dom/server'
import {connect} from 'react-redux';
import _ from 'lodash';
import ActionsUtils from '../../actions/ActionsUtils';
import ProcessUtils from '../../common/ProcessUtils';
import ExpressionSuggester from './ExpressionSuggester'

import AceEditor from 'react-ace';
import 'brace/mode/jsx';

import 'brace/ext/language_tools'
import 'brace/ext/searchbox';

import '../../brace/mode/spel'
import '../../brace/mode/sql'
import '../../brace/theme/nussknacker'

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

var inputExprIdCounter = 0
class ExpressionSuggest extends React.Component {

  static propTypes = {
    inputProps: PropTypes.object.isRequired,
    fieldName: PropTypes.string
  }

  customAceEditorCompleter = {
    getCompletions: (editor, session, caretPosition2d, prefix, callback) => {
      const suggestions = this.expressionSuggester.suggestionsFor(this.state.value, caretPosition2d)
      callback(null, _.map(suggestions, (s) => {
        //unfortunately Ace treats `#` as special case, we have to remove `#` from suggestions or it will be duplicated
        //maybe it depends on language mode?
        const methodName = s.methodName.replace("#", "")
        const returnType = ProcessUtils.humanReadableType(s.refClazz)
        return {name: methodName, value: methodName, score: 1, meta: returnType, description: s.description, parameters: s.parameters, returnType: returnType}
      }))
    },
    getDocTooltip: (item) => {
      if (item.description || !_.isEmpty(item.parameters)) {
        const paramsSignature = item.parameters.map(p => ProcessUtils.humanReadableType(p.refClazz) + " " + p.name).join(", ")
        const javaStyleSignature = `${item.returnType} ${item.name}(${paramsSignature})`
        item.docHTML = ReactDOMServer.renderToStaticMarkup((
          <div className="function-docs">
            <b>{javaStyleSignature}</b>
            <hr/>
            <p>{item.description}</p>
          </div>
        ))
      }
    }
  }

  constructor(props) {
    super(props);
    inputExprIdCounter+=1;
    this.state = {
      value: props.inputProps.value,
      id: "inputExpr" + inputExprIdCounter
    };
    this.expressionSuggester = this.createExpressionSuggester(props)
  }

  //fixme is this enough?
  //this shouldComponentUpdate is for cases when there are multiple instances of suggestion component in one view and to make them not interfere with each other
  //fixme maybe use this.state.id here?
  shouldComponentUpdate(nextProps, nextState) {
    return !(_.isEqual(this.state.value, nextState.value))
  }

  componentDidUpdate(prevProps, prevState) {
    this.expressionSuggester = this.createExpressionSuggester(this.props)
    if (!_.isEqual(this.state.value, prevState.value)) {
      this.props.inputProps.onValueChange(this.state.value)
    }
  }

  createExpressionSuggester = (props) => {
    return new ExpressionSuggester(props.typesInformation, props.variables);
  }

  onChange = (newValue) => {
    this.setState({
      value: newValue
    })
  }

  render() {
    if (this.props.dataResolved) {
      return (
        <div style={{paddingTop: 10, paddingBottom: 10, paddingLeft: 20 - 4, paddingRight: 20 - 4, backgroundColor: '#333', borderBottom: '1px solid #808080'}}>
          <AceEditor
            mode={this.props.inputProps.language}
            width={"100%"}
            minLines={1}
            maxLines={50}
            theme={'nussknacker'}
            onChange={this.onChange}
            value={this.state.value}
            showPrintMargin={false}
            cursorStart={-1} //line start
            showGutter={false}
            highlightActiveLine={false}
            highlightGutterLine={false}
            wrapEnabled={true}
            editorProps={{
              $blockScrolling: "Infinity"
            }}
            setOptions={{
              indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
              enableBasicAutocompletion: [this.customAceEditorCompleter],
              enableLiveAutocompletion: true,
              enableSnippets: false,
              showLineNumbers: false,
              fontSize: 16,
              fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace", //monospace font seems to be mandatory to make ace cursor work well,
              readOnly: this.props.inputProps.readOnly
            }}
          />
        </div>
        )
    } else {
      return null
    }

  }
}

function mapState(state, props) {
  const processDefinitionData = !_.isEmpty(state.settings.processDefinitionData) ? state.settings.processDefinitionData : {processDefinition: { typesInformation: []}}
  const dataResolved = !_.isEmpty(state.settings.processDefinitionData)
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  const variablesForNode = state.graphReducer.nodeToDisplay.id || _.get(state.graphReducer, ".edgeToDisplay.to") || null
  const variables = ProcessUtils.findAvailableVariables(variablesForNode, state.graphReducer.processToDisplay, processDefinitionData.processDefinition, props.fieldName)

  return {
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    variables: variables
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest);