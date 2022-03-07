import Highlighter from "react-highlight-words";
import React from "react";
import { Highlight } from "./utils";

export function Highlighted2({ value, filter }: { value: string; filter: string }): JSX.Element {
    return (
        <Highlighter
            autoEscape
            textToHighlight={value.toString()}
            searchWords={filter?.toString().split(/\s/) || []}
            highlightTag={Highlight}
        />
    );
}
