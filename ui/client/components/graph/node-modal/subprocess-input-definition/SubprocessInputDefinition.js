import React from "react"
import _ from "lodash"
import PropTypes from "prop-types"
import {connect} from "react-redux"
import {v4 as uuid4} from "uuid"
import LabeledInput from "../editors/field/LabeledInput"
import LabeledTextarea from "../editors/field/LabeledTextarea"
import FieldsSelect from "./FieldsSelect"
import ProcessUtils from "../../../../common/ProcessUtils"
import ActionsUtils from "../../../../actions/ActionsUtils"
import {errorValidator, notEmptyValidator} from "../../../../common/Validators"

class SubprocessInputDefinition extends React.Component {

  static propTypes = {
    addElement: PropTypes.func.isRequired,
    isMarked: PropTypes.func.isRequired,
    node: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    readOnly: PropTypes.bool,
    removeElement: PropTypes.func.isRequired,
    showValidation: PropTypes.bool.isRequired,
    typesInformation: PropTypes.array.isRequired,
  }

  constructor(props) {
    super(props)
    this.typeOptions = this.getTypeOptions(this.props.typesInformation, (type) => ({
      value: type.clazzName.refClazzName,
      label: ProcessUtils.humanReadableType(type.clazzName.refClazzName),
    }))

    this.defaultTypeOption = _.find(this.typeOptions, {label: "String"}) || _.head(this.typeOptions)
  }

  getTypeOptions = (values, mapFunc) => {
    const mappedValues = _.map(values, (value) => (mapFunc(value)))
    return _.orderBy(mappedValues, (item) => [item.label, item.value], ["asc"])
  }

  render() {
    const {addElement, isMarked, node, onChange, readOnly, removeElement, showValidation, errors, renderFieldLabel} = this.props

    const addField = () => {
      addElement("parameters", {"name": "", "uuid": uuid4(), typ: {refClazzName: this.defaultTypeOption.value}})
    }

    return (
      <div className="node-table-body">
        <LabeledInput renderFieldLabel={() => renderFieldLabel("Name")}
                      value={node.id}
                      path="id"
                      onChange={onChange}
                      isMarked={isMarked("id")}
                      readOnly={readOnly}
                      showValidation={showValidation}
                      validators={[notEmptyValidator, errorValidator(errors, "Id")]}/>

        <FieldsSelect label="Parameters"
                      onChange={onChange}
                      addField={addField}
                      removeField={removeElement}
                      namespace={"parameters"}
                      fields={node.parameters || []}
                      options={this.typeOptions}
                      isMarked={index => isMarked(`parameters[${index}].name`) || isMarked(`parameters[${index}].typ.refClazzName`)}
                      toogleCloseOnEsc={this.props.toogleCloseOnEsc}
                      showValidation={showValidation}
                      readOnly={readOnly}/>

        <LabeledTextarea renderFieldLabel={() => renderFieldLabel("Description")}
                         value={_.get(node, "additionalFields.description", "")}
                         path="additionalFields.description"
                         className={"node-input"}
                         onChange={onChange}
                         isMarked={isMarked("additionalFields.description")}
                         readOnly={readOnly}/>

        {/*placeholder for Select drop-down menu*/}
        <div className="drop-down-menu-placeholder"/>
      </div>
    )
  }
}

function mapState(state, props) {
  const processDefinitionData = !_.isEmpty(state.settings.processDefinitionData) ? state.settings.processDefinitionData : {processDefinition: {typesInformation: []}}
  const typesInformation = processDefinitionData.processDefinition.typesInformation

  return {
    typesInformation: typesInformation,
    processingType: state.graphReducer.processToDisplay.processingType
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SubprocessInputDefinition)


