import { css, cx } from "@emotion/css";
import React, { useCallback, useEffect } from "react";
import { useSelector } from "react-redux";
import { getWritableCategories } from "../reducers/selectors/settings";
import { ChangeableValue } from "./ChangeableValue";
import { Validator } from "./graph/node-modal/editors/Validators";
import ValidationLabels from "./modals/ValidationLabels";
import { NodeTable, NodeTableBody } from "./graph/node-modal/NodeDetailsContent/NodeTable";
import { NodeInput, SelectNodeWithFocus } from "./withFocus";
import { NodeLabelStyled } from "./graph/node-modal/fragment-input-definition/NodeStyled";
import { NodeRow } from "./graph/node-modal/NodeDetailsContent/NodeStyled";
import "../stylesheets/graph.styl";

type FormValue = { processId: string; processCategory: string };

interface AddProcessFormProps extends ChangeableValue<FormValue> {
    nameValidators: Validator[];
}

export function AddProcessForm({ nameValidators, value, onChange }: AddProcessFormProps): JSX.Element {
    const categories = useSelector(getWritableCategories);

    const onFieldChange = useCallback((field: keyof FormValue, next: string) => onChange({ ...value, [field]: next }), [onChange, value]);

    useEffect(() => {
        if (!value.processCategory) {
            onFieldChange("processCategory", categories[0]);
        }
    }, [categories, onFieldChange, value.processCategory]);

    return (
        <div
            className={cx(
                css({
                    minWidth: 600,
                    paddingTop: 10,
                    paddingBottom: 20,
                }),
            )}
        >
            <NodeTable>
                <NodeTableBody>
                    <NodeRow>
                        <NodeLabelStyled>Name</NodeLabelStyled>
                        <div className="node-value">
                            <NodeInput
                                type="text"
                                id="newProcessId"
                                value={value.processId}
                                onChange={(e) => onFieldChange("processId", e.target.value)}
                            />
                            <ValidationLabels validators={nameValidators} values={[value.processId]} />
                        </div>
                    </NodeRow>
                    <NodeRow>
                        <NodeLabelStyled>Category</NodeLabelStyled>
                        <div className="node-value">
                            <SelectNodeWithFocus
                                id="processCategory"
                                value={value.processCategory}
                                onChange={(e) => onFieldChange("processCategory", e.target.value)}
                            >
                                {categories.map((cat, index) => (
                                    <option key={index} value={cat}>
                                        {cat}
                                    </option>
                                ))}
                            </SelectNodeWithFocus>
                        </div>
                    </NodeRow>
                </NodeTableBody>
            </NodeTable>
        </div>
    );
}
