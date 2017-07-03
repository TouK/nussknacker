import React, {Component} from "react";
import {render} from "react-dom";
import {ListGroupItem} from "react-bootstrap";
import { connect } from 'react-redux';
import Textarea from 'react-textarea-autosize';
import _ from 'lodash';
import ActionsUtils from '../../actions/ActionsUtils';
import ProcessUtils from '../../common/ProcessUtils';
import ExpressionSuggester from './ExpressionSuggester'
import Autosuggest from "react-autosuggest";
import $ from "jquery";

//do poprawy
// - uwzglednic kategorie dla zmiennych globalnych?
// - moze ESC powinien byc dozwolony? tzn nie zamykalby sie modal tylko chowaloby sie podpowiadanie?
// - wiecej milosci

var inputExprIdCounter = 0
class ExpressionSuggest extends React.Component {

  static propTypes = {
    inputProps: React.PropTypes.object.isRequired
  }

  constructor(props) {
    super(props);
    inputExprIdCounter+=1;
    this.state = {
      value: props.inputProps.value,
      suggestions: [],
      expectedCaretPosition: 0,
      id: "inputExpr" + inputExprIdCounter
    };
    this.expressionSuggester = this.createExpressionSuggester(props)
  }

  //fixme czy o wystarczy?
  // to jest po to, zeby przy wielu instancjach tego komponentu na stronie do rerenderowania brac pod uwage tylko te ktore naprawde mogly sie zmienic
  shouldComponentUpdate(nextProps, nextState) {
    return !(_.isEqual(this.state.suggestions, nextState.suggestions) &&
      _.isEqual(this.state.expectedCaretPosition, nextState.expectedCaretPosition) &&
      _.isEqual(this.state.value, nextState.value)
    )
  }

  componentDidUpdate(prevProps, prevState) {
    this.expressionSuggester = this.createExpressionSuggester(this.props)
    this.setCaretPosition(this.state.expectedCaretPosition)
    if (!_.isEqual(this.state.value, prevState.value)) {
      this.props.inputProps.onValueChange(this.state.value)
    }
  }

  createExpressionSuggester = (props) => {
    return new ExpressionSuggester(props.typesInformation, props.variables);
  }

  getSuggestionValue = suggestion => {
    return this.state.value
  }

  renderSuggestion = suggestion => {
    const {start, middle, end} = this.expressionSuggester.extractMatchingPartFromInput(suggestion, this.state.value, this.getCaretPosition())
    const suggestionType = ProcessUtils.humanReadableType(suggestion.refClazzName)
    return (
      start || middle || end ?
        <div>
          {start}<b>{middle}</b>{end}<span className="typeSuggestion">{suggestionType}</span>
        </div> :
        <div>{suggestion.methodName}{suggestionType}</div>
    );
  }

  renderInputComponent = inputProps => {
    return (
      <div>
        <Textarea id={this.state.id} {...inputProps} />
      </div>
    )
  }

  onSuggestionSelected = (event, { suggestion, suggestionValue, sectionIndex, method }) => {
    event.preventDefault() //zeby przy textarea po wybraniu podpowiadanej opcji nie przechodzic do nowej linii
    const suggestionApplied = this.expressionSuggester.applySuggestion(suggestion, this.state.value, this.getCaretPosition())
    this.setState({
      value: suggestionApplied.value,
      expectedCaretPosition: suggestionApplied.caretPosition
    })
  }

  onSuggestionsFetchRequested = ({value}) => {
    const suggestions = this.expressionSuggester.suggestionsFor(value, this.getCaretPosition())
    this.setState({
      suggestions: suggestions
    })
  }

  //fixme jak to zamienic na ref?
  getInputExprElement = () => {
    return $('#' + this.state.id)[0]
  }

  getCaretPosition = () => {
    return this.getInputExprElement().selectionStart
  }

  setCaretPosition = (position) => {
    this.getInputExprElement().setSelectionRange(position, position)
  }

  onChange = (newValue) => {
    this.setState({
      value: newValue,
      expectedCaretPosition: this.getCaretPosition()
    })
  }

  onSuggestionsClearRequested = () => {
    this.setState({
      suggestions: []
    });
  };

  render() {
    if (this.props.dataResolved) {
      const inputProps = {
        ..._.omit(this.props.inputProps, "onValueChange"), //wyrzucamy bo pluje warningami
        value: this.state.value,
        onChange: (event, {newValue}) => {
          this.onChange(newValue)
        }
    }
      return (
        <div>
          <Autosuggest
            id={"autosuggest-" + this.props.id}
            suggestions={this.state.suggestions}
            onSuggestionsFetchRequested={this.onSuggestionsFetchRequested}
            onSuggestionsClearRequested={this.onSuggestionsClearRequested}
            getSuggestionValue={this.getSuggestionValue}
            renderSuggestion={this.renderSuggestion}
            shouldRenderSuggestions={() => {return true}}
            renderInputComponent={this.renderInputComponent}
            inputProps={inputProps}
            onSuggestionSelected={this.onSuggestionSelected}
          />

        </div>
      );

    } else {
      return null
    }

  }

}


function mapState(state) {
  const processDefinitionData = !_.isEmpty(state.settings.processDefinitionData) ? state.settings.processDefinitionData
    : {processDefinition: { typesInformation: []}}
  const dataResolved = !_.isEmpty(state.settings.processDefinitionData)
  const typesInformation = processDefinitionData.processDefinition.typesInformation
  const variablesForNode = state.graphReducer.nodeToDisplay.id || state.graphReducer.edgeToDisplay.to
  const variables = ProcessUtils.findAvailableVariables(variablesForNode, state.graphReducer.processToDisplay, processDefinitionData.processDefinition)
  return {
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    variables: variables
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest);