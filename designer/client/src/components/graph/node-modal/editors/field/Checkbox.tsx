import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import React, { useId } from "react";

import { NodeInput } from "../../../../FormElements";
import { nodeValue } from "../../NodeDetailsContent/NodeTableStyled";
import { FormControl } from "../FormControl";
import type { LabeledInputProps } from "./LabeledInput";

export interface CheckboxProps extends Pick<LabeledInputProps, "children" | "autoFocus" | "isMarked" | "onChange" | "readOnly"> {
    value?: boolean;
    checked?: boolean;
    className?: string;
    description?: string;
}

export default function Checkbox(props: CheckboxProps): React.JSX.Element {
    const { className, description, children, autoFocus, isMarked, value, onChange, readOnly } = props;
    const id = useId();
    return (
        <FormControl>
            {children}
            <Box className={cx(nodeValue, { marked: isMarked, "read-only": readOnly })}>
                <Box
                    sx={{
                        display: "flex",
                        alignItems: "center",
                        columnGap: 1,
                    }}
                >
                    <NodeInput
                        id={id}
                        autoFocus={autoFocus}
                        type="checkbox"
                        checked={!!value}
                        onChange={onChange}
                        disabled={readOnly}
                        className={className}
                    />
                    {description ? <label htmlFor={id}>{description}</label> : null}
                </Box>
            </Box>
        </FormControl>
    );
}
