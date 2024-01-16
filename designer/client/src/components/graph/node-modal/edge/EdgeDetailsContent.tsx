import React, { useCallback } from "react";
import { Edge, EdgeKind, EdgeType } from "../../../../types";
import BaseModalContent from "../BaseModalContent";
import EditableEditor from "../editors/EditableEditor";
import { useDiffMark } from "../PathsToMark";
import { getValidationErrorsForField } from "../editors/Validators";
import { FormLabel } from "@mui/material";

interface Props {
    edge: Edge;
    readOnly?: boolean;
    changeEdgeTypeValue: (type: EdgeType["type"]) => void;
    changeEdgeTypeCondition: (condition: EdgeType["condition"]["expression"]) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    variableTypes;
    edgeErrors?;
}

export default function EdgeDetailsContent(props: Props): JSX.Element | null {
    const { edge, edgeErrors, readOnly, changeEdgeTypeCondition, showValidation, showSwitch, changeEdgeTypeValue, variableTypes } = props;

    const [isMarked] = useDiffMark();
    const renderFieldLabel = useCallback((label) => <FormLabel>{label}</FormLabel>, []);

    switch (edge.edgeType?.type) {
        case EdgeKind.switchDefault: {
            return (
                <BaseModalContent
                    edge={edge}
                    edgeErrors={edgeErrors}
                    readOnly={readOnly}
                    isMarked={isMarked}
                    changeEdgeTypeValue={changeEdgeTypeValue}
                />
            );
        }
        case EdgeKind.switchNext: {
            const expressionObj = {
                expression: edge.edgeType.condition.expression,
                language: edge.edgeType.condition.language,
            };
            return (
                <BaseModalContent
                    edge={edge}
                    edgeErrors={edgeErrors}
                    readOnly={readOnly}
                    isMarked={isMarked}
                    changeEdgeTypeValue={changeEdgeTypeValue}
                >
                    <EditableEditor
                        variableTypes={variableTypes}
                        fieldLabel={"Expression"}
                        renderFieldLabel={renderFieldLabel}
                        expressionObj={expressionObj}
                        readOnly={readOnly}
                        isMarked={isMarked("edgeType.condition.expression")}
                        showValidation={showValidation}
                        showSwitch={showSwitch}
                        onValueChange={changeEdgeTypeCondition}
                        fieldErrors={getValidationErrorsForField(edgeErrors, "edgeType.condition.expression")}
                    />
                </BaseModalContent>
            );
        }
        default:
            return null;
    }
}
