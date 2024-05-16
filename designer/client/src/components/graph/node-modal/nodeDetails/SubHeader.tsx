import React from "react";
import { Subtype } from "./Subtype";
import { Docs } from "./Docs";

enum HeaderType {
    SUBTYPE_DOCS,
    SUBTYPE,
    DOCS,
    DEFAULT,
}

const getModalHeaderType = (docsUrl?: string, nodeClass?: string) => {
    if (docsUrl && nodeClass) {
        return HeaderType.SUBTYPE_DOCS;
    }
    if (nodeClass) {
        return HeaderType.SUBTYPE;
    }
    if (docsUrl) {
        return HeaderType.DOCS;
    }
    return HeaderType.DEFAULT;
};

export const NodeDocs = ({ name, href, ...props }: { className?: string; name?: string; href?: string }) => {
    const modalHeaderType = getModalHeaderType(href, name);

    switch (modalHeaderType) {
        case HeaderType.SUBTYPE_DOCS:
        case HeaderType.DOCS:
            return (
                <Docs href={href} {...props}>
                    {name}
                </Docs>
            );
        case HeaderType.SUBTYPE:
            return <Subtype {...props}>{name}</Subtype>;
        default:
            return null;
    }
};
