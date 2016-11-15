import React, {Component} from "react";
import {render} from "react-dom";
import {ListGroupItem} from "react-bootstrap";
import { connect } from 'react-redux';
import _ from 'lodash';
import ActionsUtils from '../../actions/ActionsUtils';
import Autosuggest from "react-autosuggest";
import $ from "jquery";

//do poprawy
// - rozwiazac https://github.com/moroshko/react-autowhatever/issues/24 i usunac wlasne rozwiazanie z packages.json
// - Obslugiwanie innych zmiennych niz "input"
// - wiecej milosci
class ExpressionSuggest extends React.Component {

  static propTypes = {
    inputProps: React.PropTypes.object.isRequired
  }

  constructor(props) {
    super(props);
    this.state = {
      value: props.inputProps.value,
      suggestions: [],
      expectedCaretPosition: 0
    };
  }

  componentDidUpdate(prevProps, prevState) {
    this.setCaretPosition(this.state.expectedCaretPosition)
  }

  getSuggestionValue = suggestion => {
    const caretPosition = this.getCaretPosition()
    const beforeCaret = this.state.value.slice(0, caretPosition)
    const afterCaret = this.state.value.slice(caretPosition)
    const lastExpressionPart = this.lastExpressionPart(this.currentlyFocusedExpressionLastPart(this.state.value))
    const firstExpressionPart = _.trimEnd(beforeCaret, lastExpressionPart)
    const suggestionForLastExpression = _.concat(this.alreadyTypedProperties(lastExpressionPart), suggestion.methodName)
    const suggestionValue = this.propertiesToDotSeparated(suggestionForLastExpression)
    const suggested = _.trimStart(suggestionValue, '.');
    const newBeforeCaret = firstExpressionPart + suggested
    //fixme to jest niestety bardzo slabe, ale teraz nie wiem jak to lepiej zrobic....
    //uzywamy setTimeout zeby ten setState wykonal sie PO setState w onChange
    setTimeout(() => {
      this.setState({
        expectedCaretPosition: newBeforeCaret.length
      })
    }, 30)
    return newBeforeCaret + afterCaret
  }

  renderSuggestion = suggestion => {
    return (
      <div>
        {suggestion.methodName}
      </div>
    );
  }

  renderInputComponent = inputProps => {
    return (
      <div>
        <textarea id="inputExpr" {...inputProps} />
      </div>
    )
  }

  onSuggestionSelected = (event, { suggestion, suggestionValue, sectionIndex, method }) => {
    event.preventDefault() //zeby przy textarea po wybraniu podpowiadanej opcji nie przechodzic do nowej linii
  }

  findMostParentClazz = (properties) => {
    if (_.isEqual(properties[0], "#input")) {
      return _.reduce(_.tail(properties), (currentParentClazz, prop) => {
        const parentClazz = this.getTypeInfo(currentParentClazz)
        return _.get(parentClazz.methods, `${prop}.refClazzName`) || ""
      }, this.props.sourceClazzName)
    } else if (_.isEqual(properties, ["#input"])) {
      return this.props.sourceClazzName
    } else {
      return null
    }
  }

  onSuggestionsFetchRequested = ({ value }) => {
    if (!this.props.inputProps.readOnly) {
      const lastExpressionPart = this.lastExpressionPart(this.currentlyFocusedExpressionLastPart(value))
      const properties = this.alreadyTypedProperties(lastExpressionPart)
      const focusedClazz = this.findMostParentClazz(properties)
      const suggestions = this.getSuggestions(lastExpressionPart, focusedClazz)
      this.setState({
        suggestions: suggestions
      })
    }
  }

  //fixme jak to zamienic na ref?
  getInputExprElement = () => {
    return $('#inputExpr')[0]
  }

  getCaretPosition = () => {
    return this.getInputExprElement().selectionStart
  }

  setCaretPosition = (position) => {
    this.getInputExprElement().setSelectionRange(position, position)
  }

  currentlyFocusedExpressionLastPart = (value) => {
    return value.slice(0, this.getCaretPosition())
  }

  getSuggestions = (value, focusedClazz) => {
    if (_.includes(value, "#input.") && focusedClazz) {
      const currentType = this.getTypeInfo(focusedClazz)
      const inputValue = this.justTypedProperty(value).trim()
      const allowedMethodList = _.map(currentType.methods, (val, key) => {
        return { ...val, methodName: key}
      })
      return inputValue.length === 0 ? allowedMethodList : _.filter(allowedMethodList, (method) => {
        return _.includes(method.methodName.toLowerCase(), inputValue.toLowerCase())
      })
    } else if (_.includes("#input", value.toLowerCase())) {
      return [{ methodName: "#input"}]
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
    return "#" + _.last(_.split(value, '#'))
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

  onChange = (event, { newValue }) => {
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
        ...this.props.inputProps,
        value: this.state.value,
        onChange: (event, {newValue}) => {
          this.onChange(event, {newValue})
          this.props.inputProps.onChange(newValue)
        }
    }
      return (
        <div>
          <Autosuggest
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
    : {processDefinition: { sourceFactories: [], typesInformation: []}}
  const dataResolved = !_.isEmpty(state.settings.processDefinitionData)
  const sourceFactories = processDefinitionData.processDefinition.sourceFactories;
  const typesInformation = processDefinitionData.processDefinition.typesInformation;
  const sourceType = state.graphReducer.processToDisplay.nodes[0].ref.typ
  const source = _.get(sourceFactories, sourceType);
  const sourceClazzName = _.isEmpty(source) ? null : source.definedClass.refClazzName
  const sourceMethods = () => {
    const sourceDef = _.find(typesInformation, { clazzName: { refClazzName: sourceClazzName }})
    return sourceDef.methods
  }

  return {
    sourceFactories: sourceFactories,
    typesInformation: typesInformation,
    dataResolved: dataResolved,
    sourceType: sourceType,
    sourceMethods: dataResolved ? sourceMethods() : [],
    sourceClazzName: sourceClazzName
  };
}
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ExpressionSuggest);