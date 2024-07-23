/* eslint-disable i18next/no-literal-string */
import React from "react";
import Inspector, { ObjectLabel, ObjectName } from "react-inspector";

const defaultNodeRenderer = ({ depth, name, data, isNonenumerable, expanded }) => {
    return depth === 0 ? (
        <ObjectName name={name} />
    ) : expanded ? (
        <>
            <ObjectName name={name} />:
        </>
    ) : (
        <ObjectLabel name={name} data={data} isNonenumerable={isNonenumerable} />
    );
};

export function Debug({ data, name }: { data: any; name?: string }): JSX.Element {
    return (
        <div style={{ zoom: 2 }}>
            <Inspector expandLevel={1} theme="chromeDark" data={data} name={name} nodeRenderer={defaultNodeRenderer} />
        </div>
    );
}
