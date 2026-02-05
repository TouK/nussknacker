import { styled } from "@mui/material";
import millify from "millify";
import React from "react";

import { useUserSettings } from "../../../../common/useUserSettings";

const CountComponent = ({ children, ...props }: { children: number; className?: string }) => {
    const [shortCounts] = useUserSettings("node.shortCounts");
    return (
        <span title={children.toLocaleString()} {...props}>
            {shortCounts ? millify(children, { precision: 0 }) : children}
        </span>
    );
};

export const Count = styled(CountComponent)(() => ({
    fontWeight: "bold",
    fontSize: "1.2em",
}));
