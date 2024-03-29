import React from "react";
import { NodeValue } from "../../node";
import Input, { InputProps } from "../field/Input";
import { cx } from "@emotion/css";

interface MapKeyProps extends Omit<InputProps, "onChange"> {
    onChange?: (value: string) => void;
}

export default function MapKey(props: MapKeyProps): JSX.Element {
    const { onChange, ...passProps } = props;
    return (
        <NodeValue className={cx("fieldName", passProps.className)}>
            <Input {...passProps} placeholder="Field name" onChange={(e) => onChange(e.target.value)} />
        </NodeValue>
    );
}
