import { css, cx } from "@emotion/css";
import React, { useCallback, useEffect } from "react";
import { useSelector } from "react-redux";
import { getWritableCategories } from "../reducers/selectors/settings";
import { ChangeableValue } from "./ChangeableValue";
import ValidationLabels from "./modals/ValidationLabels";
import { NodeTable, NodeTableBody } from "./graph/node-modal/NodeDetailsContent/NodeTable";
import { NodeInput, SelectNodeWithFocus } from "./withFocus";
import { NodeRow } from "./graph/node-modal/NodeDetailsContent/NodeStyled";
import { NodeLabelStyled } from "./graph/node-modal/node";
import { FieldError } from "./graph/node-modal/editors/Validators";

export type FormValue = { processName: string; processCategory: string };

interface AddProcessFormProps extends ChangeableValue<FormValue> {
    fieldErrors: FieldError[];
}

export function AddProcessForm({ value, onChange, fieldErrors }: AddProcessFormProps): JSX.Element {
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
                                id="newProcessName"
                                value={value.processName}
                                onChange={(e) => onFieldChange("processName", e.target.value)}
                            />
                            <ValidationLabels fieldErrors={fieldErrors} />
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
