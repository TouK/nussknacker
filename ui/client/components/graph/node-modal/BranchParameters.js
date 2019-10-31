import PropTypes from "prop-types"
import React from "react"
import ExpressionSuggest from "../ExpressionSuggest"
import {notEmptyValidator} from "../../../common/Validators";

const BranchParameters = (props) => {

    const {node, joinDef, onChange, isMarked, readOnly, handlePropertyValidation} = props

    return (
        joinDef.branchParameters.map((branchParamDef, paramIndex) => {
            return (
                <div className="node-row" key={branchParamDef.name}>
                    <div className="node-label" title={branchParamDef.name}>{branchParamDef.name}:</div>
                    <div className="node-value">
                        <div className="fieldsControl">
                            {
                                joinDef.incomingEdges.map((edge, edgeIndex) => {
                                    // It could be tricky - we assume that node data is filled by template (or actual values)
                                    // in the same order as here, but it is true because for filling is used the same JoinDef
                                    const path = `branchParameters[${edgeIndex}].parameters[${paramIndex}]`
                                    const paramValue = node.branchParameters[edgeIndex].parameters[paramIndex]

                                    return (
                                        <div className="node-row" key={`${branchParamDef.name}-${edge.from}`}>
                                            <div className={"node-value fieldName" + (isMarked(path) ? " marked" : "")}>
                                                <input
                                                    className="node-input"
                                                    type="text"
                                                    value={edge.from}
                                                    placeholder="branchId"
                                                    readOnly={true}
                                                />
                                            </div>
                                            <div className={"node-value field" + (isMarked(path) ? " marked" : "")}>
                                                <ExpressionSuggest
                                                    fieldName={`value-${branchParamDef.name}-${edge.from}`}
                                                    inputProps={{
                                                        onValueChange: ((value) => {
                                                          onChange(`${path}.expression.expression`, value)
                                                          handlePropertyValidation(`${path}.expression.expression`, notEmptyValidator.isValid(value))
                                                        }),
                                                        value: paramValue.expression.expression,
                                                        language: paramValue.expression.language,
                                                        readOnly}}
                                                    validators={[notEmptyValidator]}
                                                />
                                            </div>
                                        </div>
                                    )}
                                )
                            }
                        </div>
                    </div>
                </div>
            );
        }))
}

BranchParameters.propTypes = {
    node: PropTypes.object.isRequired,
    joinDef: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired,
    isMarked: PropTypes.func.isRequired,
    readOnly: PropTypes.bool,
}

BranchParameters.defaultProps = {
    readOnly: false
}

export default BranchParameters