import type { ComponentType } from "react";
import React, { type ComponentProps } from "react";

import type { Prettify } from "../../useNodeTypeDetailsContentLogic";
import { editors } from "./Editor";
import type { EditorType } from "./types";

type TypeOfEditor<T extends keyof typeof editors> = (typeof editors)[T];
type PropsOfEditor<T extends keyof typeof editors> = ComponentProps<TypeOfEditor<T>>;

export function getEditorByType<T extends EditorType>(type: T) {
    return editors[type]();
}

type WithoutEditorConfig<T extends EditorType> = NonNullable<Omit<PropsOfEditor<T>, "editorConfig">>;
type EditorConfigWithoutType<T extends EditorType> = Prettify<Omit<PropsOfEditor<T>["editorConfig"], "type">>;
type Props<T extends EditorType> = Prettify<WithoutEditorConfig<T> & { type: T; config: EditorConfigWithoutType<T> }>;

export function EditorByType<T extends EditorType>({ type, config, ...props }: Props<T>) {
    const passProps = { ...props, editorConfig: { type, ...config } } as PropsOfEditor<T>;

    const Editor = getEditorByType(type) as ComponentType<PropsOfEditor<T>>;
    if (!Editor) return null;

    return <Editor {...passProps} />;
}
