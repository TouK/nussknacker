import React, { forwardRef } from "react";
import ReactAce from "react-ace/lib/ace";
import { RawEditor, RawEditorProps } from "../../editors/expression/RawEditor";
import { ExpressionLang } from "../../editors/expression/types";

type EditorProps = Omit<RawEditorProps, "expressionObj" | "onValueChange" | "fieldErrors"> & {
    value: string;
    onChange: (value: string) => void;
};

export const Editor = forwardRef<ReactAce, EditorProps>(function Editor(props, ref) {
    const { value, onChange, ...passProps } = props;
    return (
        <RawEditor
            ref={ref}
            {...passProps}
            fieldErrors={[]}
            expressionObj={{
                expression: value,
                language: ExpressionLang.SpEL,
            }}
            onValueChange={onChange}
        />
    );
});
