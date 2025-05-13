import React from "react";

import { FieldLabel } from "../graph/node-modal/FieldLabel";

interface Props {
    label: string;
}

export const SearchLabel = (props: Props) => {
    return <FieldLabel title={props.label} label={props.label} />;
};
