import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Typography } from "@mui/material";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import type { Theme } from "@mui/material/styles";
import type { SxProps } from "@mui/system";
import type { PropsWithChildren } from "react";
import React from "react";

interface Props {
    componentId: string;
    expandableTitle: string;
    onChange?: (isExpanded: boolean) => void;
    expanded?: boolean;
    expandIconSx?: SxProps<Theme>;
    typographySx?: SxProps<Theme>;
    detailsSx?: SxProps<Theme>;
}

export function Expandable({
    children,
    componentId,
    expandableTitle,
    onChange,
    expanded,
    expandIconSx = { color: "text.secondary" },
    typographySx = { typography: "body2", color: "text.secondary" },
    detailsSx,
}: PropsWithChildren<Props>): React.JSX.Element {
    const accordionOnChange = onChange ? (_: any, isExpanded: boolean) => onChange(isExpanded) : undefined;
    return (
        <Accordion
            disableGutters
            elevation={0}
            sx={{ border: 0, "&::before": { display: "none" } }}
            onChange={accordionOnChange}
            expanded={expanded}
        >
            <AccordionSummary
                expandIcon={<ExpandMoreIcon sx={expandIconSx} />}
                aria-controls={`${componentId}-content`}
                id={`${componentId}-header`}
                sx={{ flexDirection: "row-reverse", border: 0, padding: 0 }}
            >
                <Typography sx={typographySx}>{expandableTitle}</Typography>
            </AccordionSummary>
            <AccordionDetails sx={detailsSx}>{children}</AccordionDetails>
        </Accordion>
    );
}
