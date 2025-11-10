import type { PropsWithChildren, ReactElement, ReactNode } from "react";
import React from "react";

import { FormLabel } from "./FormControl";

type RenderFieldLabel = (text: string) => ReactElement;

const RenderFieldLabelCtx = React.createContext<RenderFieldLabel>(null);

const defaultValue = (label: ReactNode) => <FormLabel>{label}</FormLabel>;

export const FieldLabelProvider = ({ value, children }: PropsWithChildren<{ value?: RenderFieldLabel }>) => {
    return <RenderFieldLabelCtx.Provider value={value || defaultValue}>{children}</RenderFieldLabelCtx.Provider>;
};

export const FieldLabelConsumer = ({ text }: { text: string }) => (
    <RenderFieldLabelCtx.Consumer>{(value) => value?.(text)}</RenderFieldLabelCtx.Consumer>
);
