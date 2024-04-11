import { Edge, EdgeKind } from "../../../types";
import React from "react";
import { getStringEnumElement } from "../../../common/enumUtils";
import { TypeSelect } from "./fragment-input-definition/TypeSelect";

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
        <TypeSelect
            id={id}
            onChange={(value) => onChange(getStringEnumElement(EdgeKind, value))}
            value={options.find((option) => option.value === edge.edgeType.type)}
            options={options}
            fieldErrors={[]}
            readOnly={readOnly}
        />
    );
}
