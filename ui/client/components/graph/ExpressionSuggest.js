import React from "react";
import ReactDOMServer from 'react-dom/server'
import {connect} from 'react-redux';
import Textarea from 'react-textarea-autosize';
import _ from 'lodash';
import ActionsUtils from '../../actions/ActionsUtils';
import ProcessUtils from '../../common/ProcessUtils';
import ExpressionSuggester from './ExpressionSuggester'
import Autosuggest from "react-autosuggest";
import $ from "jquery";

import AceEditor from 'react-ace';
import 'brace/mode/jsx';

import 'brace/ext/language_tools'
import 'brace/ext/searchbox';

import '../../brace/mode/spel'
import '../../brace/theme/nussknacker'

//to reconsider
// - respect categories for global variables?
// - maybe ESC should be allowed to hide suggestions but leave modal open?

var inputExprIdCounter = 0
class ExpressionSuggest extends React.Component {

  static propTypes = {
    inputProps: React.PropTypes.object.isRequired
  }

  customAceEditorCompleter = {
    getCompletions: (editor, session, caretPosition2d, prefix, callback) => {
      const suggestions = this.expressionSuggester.suggestionsFor(this.state.value, caretPosition2d)
      callback(null, _.map(suggestions, (s) => {
        //unfortunately Ace treats `#` as special case, we have to remove `#` from suggestions or it will be duplicated
        //maybe it depends on language mode?
        const methodName = s.methodName.replace("#", "")
        const returnType = ProcessUtils.humanReadableType(s.refClazzName)
        return {name: methodName, value: methodName, score: 1, meta: returnType, description: s.description, parameters: s.parameters, returnType: returnType}
      }))
    },
    getDocTooltip: (item) => {
      if (item.description || !_.isEmpty(item.parameters)) {
        const paramsSignature = item.parameters.map(p => ProcessUtils.humanReadableType(p.refClazzName) + " " + p.name).join(", ")
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
      _autosuggest_suggestions: [],
      _autosuggest_expectedCaretPosition: 0,
      id: "inputExpr" + inputExprIdCounter
    };
    this.expressionSuggester = this.createExpressionSuggester(props)
  }

  //fixme is this enough?
  //this shouldComponentUpdate is for cases when there are multiple instances of suggestion component in one view and to make them not interfere with each other
  //fixme maybe use this.state.id here?
  shouldComponentUpdate(nextProps, nextState) {
    return !(_.isEqual(this.state._autosuggest_suggestions, nextState._autosuggest_suggestions) &&
      _.isEqual(this.state._autosuggest_expectedCaretPosition, nextState._autosuggest_expectedCaretPosition) &&
      _.isEqual(this.state.value, nextState.value)
    )
  }

  componentDidUpdate(prevProps, prevState) {
    this.expressionSuggester = this.createExpressionSuggester(this.props)
    this.setCaretPosition(this.state._autosuggest_expectedCaretPosition)
    if (!_.isEqual(this.state.value, prevState.value)) {
      this.props.inputProps.onValueChange(this.state.value)
    }
  }

  createExpressionSuggester = (props) => {
    return new ExpressionSuggester(props.typesInformation, props.variables);
  }

  onChange = (newValue) => {
    this.setState({
      value: newValue,
      _autosuggest_expectedCaretPosition: this._autosuggest_getCaretPosition()
    })
  }

  render() {
    if (this.props.dataResolved) {
      const inputProps = {
        ..._.omit(this.props.inputProps, "onValueChange"), //we leave this out, because warnings
        value: this.state.value,
        onChange: (event, {newValue}) => {
          this.onChange(newValue)
        }
    }
    return this.props.advancedCodeSuggestions ? (
      <div style={{paddingTop: 10, paddingBottom: 10, paddingLeft: 20 - 4, paddingRight: 20 - 4, backgroundColor: '#333'}}>
        <AceEditor
          mode={'spel'}
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
          setOptions={{
            indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
            enableBasicAutocompletion: [this.customAceEditorCompleter],
            enableLiveAutocompletion: false,
            enableSnippets: false,
            showLineNumbers: false,
            fontSize: 16,
            fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace", //monospace font seems to be mandatory to make ace cursor work well,
            readOnly: this.props.inputProps.readOnly
          }}
        />
      </div>
    ) :
      <Autosuggest
      id={"autosuggest-" + this.props.id}
      suggestions={this.state._autosuggest_suggestions}
      onSuggestionsFetchRequested={this._autosuggest_onSuggestionsFetchRequested}
      onSuggestionsClearRequested={this._autosuggest_onSuggestionsClearRequested}
      getSuggestionValue={this._autosuggest_getSuggestionValue}
      renderSuggestion={this._autosuggest_renderSuggestion}
      shouldRenderSuggestions={() => {return true}}
      renderInputComponent={this._autosuggest_renderInputComponent}
      inputProps={inputProps}
      onSuggestionSelected={this._autosuggest_onSuggestionSelected}
      />
    } else {
      return null
    }

  }

  //TODO remove autosuggest component if AceEditor will turn out to be better
  _autosuggest_onSuggestionsFetchRequested = ({value}) => {
    const caretPosition2d = {column: this._autosuggest_getCaretPosition(), row: 0 }
    const suggestions = this.expressionSuggester.suggestionsFor(value, caretPosition2d)
    this.setState({
      _autosuggest_suggestions: suggestions
    })
  }

  _autosuggest_onSuggestionsClearRequested = () => {
    this.setState({
      _autosuggest_suggestions: []
    });
  };

  _autosuggest_getSuggestionValue = suggestion => {
    return this.state.value
  }

  _autosuggest_renderSuggestion = suggestion => {
    const {start, middle, end} = this.expressionSuggester._autosuggest_extractMatchingPartFromInput(suggestion, this.state.value, this._autosuggest_getCaretPosition())
    const suggestionType = ProcessUtils.humanReadableType(suggestion.refClazzName)
    return (
      start || middle || end ?
        <div>
          {start}<b>{middle}</b>{end}<span className="typeSuggestion">{suggestionType}</span>
        </div> :
        <div>{suggestion.methodName}{suggestionType}</div>
    );
  }

  _autosuggest_renderInputComponent = inputProps => {
    return (
      <div>
        <Textarea id={this.state.id} {...inputProps} />
      </div>
    )
  }

  _autosuggest_onSuggestionSelected = (event, { suggestion, suggestionValue, sectionIndex, method }) => {
    event.preventDefault() //to prevent newline in textarea after choosing an option
    const suggestionApplied = this.expressionSuggester._autosuggest_applySuggestion(suggestion, this.state.value, this._autosuggest_getCaretPosition())
    this.setState({
      value: suggestionApplied.value,
      _autosuggest_expectedCaretPosition: suggestionApplied.caretPosition
    })
  }

  _autosuggest_getCaretPosition = () => {
    if (this._autosuggest_getInputExprElement()) {
      return this._autosuggest_getInputExprElement().selectionStart
    }
  }

  setCaretPosition = (position) => {
    if (this._autosuggest_getInputExprElement()) {
      this._autosuggest_getInputExprElement().setSelectionRange(position, position)
    }
  }

  //fixme change to ref?
  _autosuggest_getInputExprElement = () => {
    return $('#' + this.state.id)[0]
  }
}

function mapState(state) {
  const processDefinitionData = !_.isEmpty(state.settings.processDefinitionData) ? state.settings.processDefinitionData
    : {processDefinition: { typesInformation: []}}
  const dataResolved = !_.isEmpty(state.settings.processDefinitionData)
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  const variablesForNode = state.graphReducer.nodeToDisplay.id || _.get(state.graphReducer, ".edgeToDisplay.to") || null
  const variables = ProcessUtils.findAvailableVariables(variablesForNode, state.graphReducer.processToDisplay, processDefinitionData.processDefinition)
  return {
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    variables: variables,
    advancedCodeSuggestions: state.settings.featuresSettings.advancedCodeSuggestions
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest);