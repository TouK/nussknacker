import React from "react"
import {NodeField} from "./NodeField";
import {FieldType} from "./editors/field/Field";
import {errorValidator} from "./editors/Validators";
import {NodeType, NodeValidationError, ProcessDefinitionData} from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";

export default function ParameterOutputList({
    editedNode,
    processDefinitionData,
    fieldErrors,
    isEditMode,
    showValidation,
    renderFieldLabel,
    setProperty,
}: {
    editedNode: NodeType,
    processDefinitionData: ProcessDefinitionData,
    renderFieldLabel: (paramName: string) => JSX.Element,
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
    fieldErrors?: NodeValidationError[],
    showValidation?: boolean,
    isEditMode?: boolean,
}) {
    const parameters = ProcessUtils.findNodeObjectTypeDefinition(editedNode, processDefinitionData.processDefinition)?.outputParameters



    return parameters && parameters.length === 0 ? null : (
        <div className="node-row" key="outputParameters">
            <div className="node-label" title="Fragment outputs names">Outputs names:</div>
            <div className="node-value">
                <div className="fieldsControl">
                    {
                        parameters.map(paramName => {
                            const paramProperty = `ref.outputParameters.${paramName}`
                            return (<NodeField
                                    key={"outputParameters-" + paramName}
                                    isEditMode={isEditMode}
                                    showValidation={showValidation}
                                    node={editedNode}
                                    renderFieldLabel={renderFieldLabel}
                                    setProperty={setProperty}
                                    fieldType={FieldType.input}
                                    fieldLabel={paramName}
                                    fieldProperty={paramProperty}
                                    validators={[errorValidator(fieldErrors || [], paramProperty)]}
                                />
                            )
                        })
                    }
                </div>
            </div>
        </div>
    )
}
