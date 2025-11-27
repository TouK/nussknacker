import { cx } from "@emotion/css";
import React from "react";

import { NodeValue } from "../../node/NodeValue";
import type { InputProps } from "../field/Input";
import Input from "../field/Input";

interface MapKeyProps extends Omit<InputProps, "onChange"> {
    onChange?: (value: string) => void;
}

export default function MapKey(props: MapKeyProps): React.JSX.Element {
    const { onChange, ...passProps } = props;
    return (
        <NodeValue className={cx("fieldName", passProps.className)}>
            <Input {...passProps} placeholder="Field name" onChange={(e) => onChange(e.target.value)} />
        </NodeValue>
    );
}
