import { Edge, EdgeKind } from "../../../types";
import { SelectNodeWithFocus } from "../../withFocus";
import React from "react";
import { getStringEnumElement } from "../../../common/enumUtils";

export interface EdgeTypeOption {
    value: EdgeKind;
    label: string;
    disabled?: boolean;
}

interface Props {
    id?: string;
    readOnly?: boolean;
    edge: Edge;
    onChange: (value: EdgeKind) => void;
    options: EdgeTypeOption[];
}

export function EdgeTypeSelect(props: Props): JSX.Element {
    const { readOnly, edge, onChange, id, options } = props;
    return (
        <SelectNodeWithFocus
            id={id}
            disabled={readOnly}
            value={edge.edgeType.type}
            onChange={(e) => onChange(getStringEnumElement(EdgeKind, e.target.value))}
        >
            {options.map((o) => (
                <option key={o.value} value={o.value} disabled={o.disabled}>
                    {o.label}
                </option>
            ))}
        </SelectNodeWithFocus>
    );
}
