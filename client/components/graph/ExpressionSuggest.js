import React, {Component} from "react";
import {render} from "react-dom";
import {ListGroupItem} from "react-bootstrap";
import { connect } from 'react-redux';
import _ from 'lodash';
import ActionsUtils from '../../actions/ActionsUtils';
import ProcessUtils from '../../common/ProcessUtils';
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
  }

  componentDidUpdate(prevProps, prevState) {
    this.setCaretPosition(this.state.expectedCaretPosition)
  }

  getSuggestionValue = suggestion => {
    return this.state.value
  }

  renderSuggestion = suggestion => {
    const justTyped = this.justTypedProperty(this.focusedLastExpressionPart(this.state.value))
    const expr = new RegExp(`(.*?)${justTyped}(.*)`, "i")
    const suggestedStartAndEnd = suggestion.methodName.match(expr)
    const start = _.nth(suggestedStartAndEnd, 1)
    const end = _.nth(suggestedStartAndEnd, 2)
    const matchStartIdx = _.get(start, 'length')
    const matchEndIdx = suggestion.methodName.length - _.get(end, 'length')
    const middle = suggestion.methodName.slice(matchStartIdx, matchEndIdx)
    return (
      start || middle || end ?
        <div>
          {start}<b>{middle}</b>{end}
        </div> :
        <div>{suggestion.methodName}</div>
    );
  }

  renderInputComponent = inputProps => {
    return (
      <div>
        <textarea id={this.state.id} {...inputProps} />
      </div>
    )
  }

  onSuggestionSelected = (event, { suggestion, suggestionValue, sectionIndex, method }) => {
    event.preventDefault() //zeby przy textarea po wybraniu podpowiadanej opcji nie przechodzic do nowej linii
    const caretPosition = this.getCaretPosition()
    const beforeCaret = this.state.value.slice(0, caretPosition)
    const afterCaret = this.state.value.slice(caretPosition)
    const lastExpressionPart = this.focusedLastExpressionPart(this.state.value)
    const firstExpressionPart = _.trimEnd(beforeCaret, lastExpressionPart)
    const suggestionForLastExpression = _.concat(this.alreadyTypedProperties(lastExpressionPart), suggestion.methodName)
    const suggestionValueText = this.propertiesToDotSeparated(suggestionForLastExpression)
    const newBeforeCaret = firstExpressionPart + suggestionValueText
    this.setState({
      value: newBeforeCaret + afterCaret,
      expectedCaretPosition: newBeforeCaret.length
    })
  }

  findMostParentClazz = (properties) => {
    const variableName = properties[0]
    if (_.has(this.props.variables, variableName)) {
      const variableClazzName = _.get(this.props.variables, variableName)
      return _.reduce(_.tail(properties), (currentParentClazz, prop) => {
        const parentClazz = this.getTypeInfo(currentParentClazz)
        return _.get(parentClazz.methods, `${prop}.refClazzName`) || ""
      }, variableClazzName)
    } else {
      return null
    }
  }

  onSuggestionsFetchRequested = ({value}) => {
    if (!this.props.inputProps.readOnly) {
      const lastExpressionPart = this.focusedLastExpressionPart(value)
      const properties = this.alreadyTypedProperties(lastExpressionPart)
      const focusedClazz = this.findMostParentClazz(properties)
      const suggestions = this.getSuggestions(lastExpressionPart, focusedClazz)
      this.setState({
        suggestions: suggestions
      })
    }
  }

  focusedLastExpressionPart = (expression) => {
    return this.lastExpressionPart(this.currentlyFocusedExpressionPart(expression))
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

  currentlyFocusedExpressionPart = (value) => {
    return value.slice(0, this.getCaretPosition())
  }

  getSuggestions = (value, focusedClazz) => {
    const variableNames = _.keys(this.props.variables)
    const variableAlreadySelected = _.some(variableNames, (variable) => { return _.includes(value, `${variable}.`) })
    const variableNotSelected = _.some(variableNames, (variable) => { return _.startsWith(variable, value.toLowerCase()) })
    if (variableAlreadySelected && focusedClazz) {
      const currentType = this.getTypeInfo(focusedClazz)
      const inputValue = this.justTypedProperty(value).trim()
      const allowedMethodList = _.map(currentType.methods, (val, key) => { return { ...val, methodName: key} })
      return inputValue.length === 0 ? allowedMethodList : _.filter(allowedMethodList, (method) => {
        //fixme zamienic na regexa tutaj zeby bylo spojnie z tym co wyzej?
        return _.includes(method.methodName.toLowerCase(), inputValue.toLowerCase())
      })
    } else if (variableNotSelected && !_.isEmpty(value)) {
      return _.map(variableNames, (variableName) => { return { methodName: variableName}})
    }
    else {
      return []
    }
  }

  getTypeInfo = (clazzName) => {
    const typeInfo = _.find(this.props.typesInformation, { clazzName: { refClazzName: clazzName }})
    return !_.isEmpty(typeInfo) ? typeInfo : {
      clazzName: {
        refClazzName: clazzName,
        methods: []
      }}
  }

  lastExpressionPart = (value) => {
    return _.isEmpty(value) ? "" : "#" + _.last(_.split(value, '#'))
  }

  justTypedProperty = (value) => {
    return _.last(this.dotSeparatedToProperties(value))
  }

  alreadyTypedProperties = (value) => {
    return _.initial(this.dotSeparatedToProperties(value))
  }

  dotSeparatedToProperties = (value) => {
    return _.split(value, ".")
  }

  propertiesToDotSeparated = (properties) => {
    return _.join(properties, ".")
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
          this.props.inputProps.onValueChange(newValue)
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
  const variables = ProcessUtils.findAvailableVariables(state.graphReducer.nodeToDisplay.id, state.graphReducer.processToDisplay, processDefinitionData.processDefinition)
  return {
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    variables: variables
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest);