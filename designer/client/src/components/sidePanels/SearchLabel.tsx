import { FieldLabel } from "../graph/node-modal/FieldLabel";
import React from "react";

interface Props {
    label: string;
}

export const SearchLabel = (props: Props) => {
    return <FieldLabel title={props.label} label={props.label} />;
};
