import React, { forwardRef } from "react";
import ReactAce from "react-ace/lib/ace";
import { SpelEditor, SpelEditorProps } from "../../editors/expression/SpelEditor";
import { ExpressionLang, ExpressionObj } from "../../editors/expression/types";

type EditorProps = Omit<SpelEditorProps, "expressionObj" | "onValueChange" | "fieldErrors"> & {
    value: string;
    onChange: (value: ExpressionObj) => void;
};

export const Editor = forwardRef<ReactAce, EditorProps>(function Editor(props, ref) {
    const { value, onChange, ...passProps } = props;
    return (
        <SpelEditor
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
