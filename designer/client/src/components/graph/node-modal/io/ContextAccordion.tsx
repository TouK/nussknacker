import { ExpandMore } from "@mui/icons-material";
import { Accordion, AccordionDetails, AccordionSummary } from "@mui/material";
import type { PropsWithChildren, ReactNode } from "react";
import React, { useLayoutEffect, useRef } from "react";

export const ContextAccordion = ({
    disabled,
    expanded,
    onToggle,
    children,
    title,
}: PropsWithChildren<{
    disabled?: boolean;
    expanded?: boolean;
    onToggle: () => void;
    title?: ReactNode | undefined;
}>) => {
    const accordionRef = useRef<HTMLDivElement>(null);

    useLayoutEffect(() => {
        if (!expanded || !accordionRef.current) return;
        setTimeout(() => {
            accordionRef.current.scrollIntoView({
                behavior: "smooth",
                block: "nearest",
            });
        }, 500);
    }, [expanded]);

    return (
        <Accordion
            disabled={disabled}
            expanded={expanded}
            onChange={onToggle}
            slotProps={{
                transition: {
                    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                    // @ts-ignore
                    unmountOnExit: true,
                },
            }}
            ref={accordionRef}
            // disableGutters
            sx={{
                zoom: 0.75,
            }}
        >
            <AccordionSummary expandIcon={<ExpandMore />} sx={{ overflow: "hidden" }}>
                {title}
            </AccordionSummary>
            <AccordionDetails sx={{ padding: 0 }}>{children}</AccordionDetails>
        </Accordion>
    );
};
