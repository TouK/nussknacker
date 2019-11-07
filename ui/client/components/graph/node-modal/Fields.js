import PropTypes from "prop-types"
import React from "react"
import ExpressionSuggest from "../ExpressionSuggest"
import {notEmptyValidator} from "../../../common/Validators";
import ValidationLabels from "../../modals/ValidationLabels";

const Fields = (props) => {

    const {label, fields, onChange, addField, removeField, namespace, isMarked, readOnly, expressionValue} = props

    return (
        <div className="node-row">
            <div className="node-label" title={label}>{label}:</div>
            <div className="node-value">
                <div className="fieldsControl">
                    {
                        fields.map((field, index) => {
                            const expression = field.expression
                            const paths = `${namespace}[${index}]`

                            return (
                                <div className="node-row movable-row" key={field.uuid}>
                                    <div className={"node-value fieldName" + (isMarked(paths) ? " marked" : "")}>
                                        <input
                                            className="node-input"
                                            type="text"
                                            value={field.name}
                                            placeholder="Field name"
                                            onChange={((e) => onChange(`${paths}.name`, e.target.value))}
                                            readOnly={readOnly}
                                        />
                                        <ValidationLabels validators={[notEmptyValidator]} values={[field.name]}/>
                                    </div>
                                    <div className={"node-value field" + (isMarked(paths) ? " marked" : "")}>
                                        <ExpressionSuggest
                                            fieldName={`value-${field.uuid}`}
                                            inputProps={{
                                                onValueChange: ((value) => onChange(`${paths}.expression.expression`, value)),
                                                value: expression.expression,
                                                language: expression.language,
                                                readOnly}}
                                            validators={[notEmptyValidator]}
                                        />
                                    </div>
                                    <div className={"node-value fieldRemove" + (isMarked(paths) ? " marked" : "")}>
                                        <button
                                            className="addRemoveButton"
                                            title="Remove field"
                                            onClick={() => removeField(namespace, index)}
                                        >
                                            -
                                        </button>
                                    </div>
                                </div>
                            )}
                        )
                    }
                    <div>
                        <button onClick={addField} className="addRemoveButton" title="Add field">+</button>
                    </div>
                </div>
            </div>
        </div>
    )
}

Fields.propTypes = {
    fields: PropTypes.array.isRequired,
    label: PropTypes.string.isRequired,
    namespace: PropTypes.string.isRequired,
    isMarked: PropTypes.func.isRequired,
    onChange: PropTypes.func.isRequired,
    addField: PropTypes.func.isRequired,
    removeField: PropTypes.func.isRequired,
    readOnly: PropTypes.bool,
    expressionValue: PropTypes.bool
}

Fields.defaultProps = {
    readOnly: false
}

export default Fields