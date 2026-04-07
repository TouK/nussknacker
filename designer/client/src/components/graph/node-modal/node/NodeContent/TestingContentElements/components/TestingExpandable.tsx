import type { ComponentProps } from "react";
import React from "react";

import { Expandable } from "../../../../../../common/Expandable";

type Props = ComponentProps<typeof Expandable>;

export const TestingExpandable = ({ expandIconSx, summarySx, ...props }: Props) => (
    <Expandable
        {...props}
        summarySx={{
            pointerEvents: "none",
            "& .MuiAccordionSummary-content > *": { pointerEvents: "auto", cursor: "pointer" },
            "& .MuiAccordionSummary-expandIconWrapper": { pointerEvents: "auto", cursor: "pointer" },
            ...summarySx,
        }}
        expandIconSx={{ color: "text.secondary", pointerEvents: "auto", cursor: "pointer", ...expandIconSx }}
    />
);
