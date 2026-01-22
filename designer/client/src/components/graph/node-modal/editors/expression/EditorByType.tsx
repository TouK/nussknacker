import type { ComponentType } from "react";
import React, { type ComponentProps, useMemo } from "react";

import type { Prettify } from "../../useNodeTypeDetailsContentLogic";
import { editors, isExtendedEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { EditorType, ExpressionObj } from "./types";

type TypeOfEditor<T extends keyof typeof editors> = ReturnType<(typeof editors)[T]>;
type PropsOfEditor<T extends keyof typeof editors> = ComponentProps<TypeOfEditor<T>>;

export function getEditorByType<T extends EditorType>(type: T) {
    return editors[type]();
}

type WithoutEditorConfig<T extends EditorType> = NonNullable<Omit<PropsOfEditor<T>, "editorConfig">>;
type EditorConfigWithoutType<T extends EditorType> = Prettify<Omit<PropsOfEditor<T>["editorConfig"], "type">>;
type Props<T extends EditorType> = Prettify<WithoutEditorConfig<T> & { type: T; config: EditorConfigWithoutType<T> }>;

export function parseExpressionObjForType<T extends EditorType>(type: T, { expression, language }: ExpressionObj): ExpressionObj {
    const Editor = getEditorByType(type);
    const editorLanguage = editorsParameters[type].language;
    // if (language === editorLanguage) return { expression, language };
    if (isExtendedEditor(Editor) && Editor.parseValueOnEditorChange) {
        return Editor.parseValueOnEditorChange({ expression, language }, editorLanguage);
    }
    return { expression, language: editorLanguage };
}

export function EditorByType<T extends EditorType>({ type, config, expressionObj, ...props }: Props<T>) {
    const editorConfig = useMemo(() => ({ type, ...config }), [config, type]);

    const passProps = {
        ...props,
        editorConfig,
        expressionObj: parseExpressionObjForType(type, expressionObj),
    } as PropsOfEditor<T>;

    const Editor = getEditorByType(type) as ComponentType<PropsOfEditor<T>>;
    if (!Editor) return null;

    return <Editor {...passProps} />;
}
