import type { PropsOf } from "@emotion/react";
import React, { forwardRef } from "react";
import type ReactAce from "react-ace/lib/ace";

import { SpelEditor } from "../../editors/expression/SpelEditor";
import type { ExpressionObj } from "../../editors/expression/types";
import { ExpressionLang } from "../../editors/expression/types";

type EditorProps = Omit<PropsOf<typeof SpelEditor>, "expressionObj" | "onValueChange" | "fieldErrors"> & {
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
