import { Collapse, Stack } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useArrayState, useFocusWithin } from "rooks";

import { getBorderColor } from "../../../../../containers/theme/helpers";
import type { VariableTypes } from "../../../../../types/validation";
import type { ValueFieldProps } from "../../../../valueField";
import { rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import { Editor } from "./editor";
import { SpelChip } from "./spelChip";
import { ValuesList } from "./valuesList";

type CollectionFieldProps = ValueFieldProps<string[]> & {
    variableTypes: VariableTypes;
    disabled?: boolean;
};

export function CollectionField({ value, onChange, variableTypes, disabled }: CollectionFieldProps) {
    const ref = useRef<ReactAce>(null);

    const [values, controller] = useArrayState(value);
    const [expression, setExpression] = useState("");

    const applyValue = useCallback(
        (value: string) => {
            if (!value.trim().length) return;
            controller.push(value.trim());
            setExpression("");
        },
        [controller],
    );

    const editValue = useCallback(
        (value: string) => {
            if (!ref.current) return;
            const { editor } = ref.current;
            applyValue(expression);
            editor.focus();
            editor.setValue(value);
            editor.navigateFileEnd();
        },
        [applyValue, expression],
    );

    const addOnEnter = useMemo(
        () => ({
            name: "newLine",
            readOnly: true,
            bindKey: {
                win: "Enter",
                mac: "Enter",
            },
            exec: (editor) => applyValue(editor.getValue()),
        }),
        [applyValue],
    );

    useEffect(() => {
        const editor = ref.current?.editor;
        editor?.commands.addCommand(addOnEnter);
        return () => {
            editor?.commands.removeCommand(addOnEnter);
        };
    }, [addOnEnter, controller]);

    useEffect(() => {
        onChange(values);
    }, [onChange, values]);

    const [focusWithin, setFocusWithin] = useState(false);
    const { focusWithinProps } = useFocusWithin({
        onFocusWithinChange: setFocusWithin,
        onBlurWithin: () => applyValue(expression),
    });

    useEffect(() => {
        if (!focusWithin) return;
        ref.current?.editor.focus();
    }, [focusWithin]);

    const editorVisible = focusWithin || !values.length;
    const itemsVisible = values.length > 0;

    const onClick = useCallback(() => {
        ref.current?.editor.focus();
    }, []);

    const onEdit = useMemo(() => {
        return (i: number) => {
            editValue(values[i]);
            controller.removeItemAtIndex(i);
        };
    }, [controller, editValue, values]);

    const onRemove = useMemo(() => {
        return (i: number) => {
            controller.removeItemAtIndex(i);
        };
    }, [controller]);

    return (
        <Stack
            {...focusWithinProps}
            onClick={onClick}
            direction="column"
            sx={{
                outline: (theme) => `1px solid ${getBorderColor(theme)}`,

                ":focus, :focus-within": {
                    outline: (theme) => `1px solid ${theme.palette.primary.main}`,
                },

                [`.${rowAceEditor}`]: {
                    outline: 0,
                },
            }}
        >
            <Collapse in={editorVisible} unmountOnExit={false} mountOnEnter={false}>
                <Editor
                    ref={ref}
                    variableTypes={variableTypes}
                    value={expression}
                    onChange={({ expression }) => setExpression(expression)}
                    readOnly={!disabled}
                />
            </Collapse>
            <Collapse in={itemsVisible}>
                <ValuesList values={values} onRemove={onRemove} onEdit={onEdit} ChipComponent={SpelChip} />
            </Collapse>
        </Stack>
    );
}
