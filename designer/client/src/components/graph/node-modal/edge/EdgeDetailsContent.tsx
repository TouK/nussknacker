import { FormLabel } from "@mui/material";
import React, { useCallback } from "react";
import { Edge, EdgeKind, EdgeType } from "../../../../types";
import BaseModalContent from "../BaseModalContent";
import EditableEditor from "../editors/EditableEditor";
import { useDiffMark } from "../PathsToMark";

interface Props {
    edge: Edge;
    readOnly?: boolean;
    changeEdgeTypeValue: (type: EdgeType["type"]) => void;
    changeEdgeTypeCondition: (condition: EdgeType["condition"]["expression"]) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    variableTypes;
}

export default function EdgeDetailsContent({
    edge,
    readOnly,
    changeEdgeTypeValue,
    changeEdgeTypeCondition,
    showValidation,
    showSwitch,
    variableTypes,
}: Props): JSX.Element | null {
    const [isMarked] = useDiffMark();
    const renderFieldLabel = useCallback((label) => <FormLabel>{label}</FormLabel>, []);

    switch (edge.edgeType?.type) {
        case EdgeKind.switchDefault: {
            return (
                <BaseModalContent
                    edge={edge}
                    edgeErrors={[]}
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
                    edgeErrors={[]}
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
                        fieldErrors={[]}
                    />
                </BaseModalContent>
            );
        }
        default:
            return null;
    }
}
