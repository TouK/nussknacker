import React, { forwardRef } from "react";
import type ReactAce from "react-ace/lib/ace";

import type { VariableTypes } from "../../../../../types/validation";
import { SpelEditor } from "../../editors/expression/SpelEditor";
import type { ExpressionObj } from "../../editors/expression/types";
import { EditorType, ExpressionLang } from "../../editors/expression/types";

type EditorProps = {
    value: string;
    onChange: (value: ExpressionObj) => void;
    variableTypes: VariableTypes;
    readOnly?: boolean;
};

export const SimplifiedSpelEditor = forwardRef<ReactAce, EditorProps>(function Editor(props, ref) {
    const { value, onChange, readOnly, variableTypes } = props;
    return (
        <SpelEditor
            ref={ref}
            readOnly={readOnly}
            variableTypes={variableTypes}
            fieldErrors={[]}
            expressionObj={{
                expression: value,
                language: ExpressionLang.SpEL,
            }}
            onValueChange={onChange}
            editorConfig={{ type: EditorType.SPEL_PARAMETER_EDITOR }}
        />
    );
});
