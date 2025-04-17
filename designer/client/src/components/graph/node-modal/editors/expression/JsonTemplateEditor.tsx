import React, { useMemo } from "react";

import type { SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { SpelEditorProps } from "./SpelEditor";
import { SpelEditor } from "./SpelEditor";
import { EditorMode } from "./types";

export const JsonTemplateEditor: SimpleEditor<SpelEditorProps> = (props: SpelEditorProps) => {
    const { expressionObj, rows = 1, ...passProps } = props;

    const value = useMemo(
        () => ({
            expression: expressionObj.expression,
            language: editorsParameters.JsonTemplateParameterEditor.language,
        }),
        [expressionObj],
    );

    return (
        <SpelEditor
            {...passProps}
            expressionObj={value}
            rows={rows}
            editorMode={EditorMode.JsonTemplate}
            language={editorsParameters.JsonTemplateParameterEditor.language}
        />
    );
};
