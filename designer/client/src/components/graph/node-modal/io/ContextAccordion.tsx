import { ExpandMore } from "@mui/icons-material";
import { AccordionDetails, AccordionSummary } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { forwardRef, memo, useLayoutEffect, useRef } from "react";
import { useMergeRefs } from "rooks";

import { Initiator, startLiveData, stopLiveData } from "../../../../actions/nk/liveData";
import { useAppDispatch } from "../../../../store/storeHelpers";
import { AccordionStyled } from "./AccordionStyled";
import { ContextTitle } from "./ContextTitle";
import type { Direction, VariableContextType } from "./VariableContextTree";

type ContextAccordionProps = PropsWithChildren<{
    disabled?: boolean;
    expanded?: boolean;
    onToggle: (value: VariableContextType | null) => void;
    value: VariableContextType;
    direction: Direction;
    locked?: boolean;
    showNodes?: boolean;
}>;

export const ContextAccordion = memo(
    forwardRef<unknown, ContextAccordionProps>(function ContextAccordion(
        { disabled, expanded, onToggle, children, value, direction, locked, showNodes },
        forwardedRef,
    ) {
        const dispatch = useAppDispatch();
        const accordionRef = useRef<HTMLDivElement>(null);

        const ref = useMergeRefs(forwardedRef, accordionRef);
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
            <AccordionStyled
                disabled={disabled}
                expanded={expanded}
                onChange={(event, expanded) => {
                    const initiator = direction === "input" ? Initiator.inputAccordion : Initiator.outputAccordion;
                    if (expanded) {
                        onToggle(value);
                        dispatch(stopLiveData(initiator));
                    } else {
                        onToggle(null);
                        dispatch(startLiveData(initiator));
                    }
                }}
                slotProps={{
                    transition: {
                        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                        // @ts-ignore
                        unmountOnExit: true,
                    },
                }}
                ref={ref}
                animatedAppear
            >
                <AccordionSummary
                    expandIcon={<ExpandMore />}
                    sx={{
                        overflow: "hidden",
                    }}
                >
                    <ContextTitle reversed={direction === "input"} context={value} locked={locked} showNodes={showNodes} />
                </AccordionSummary>
                <AccordionDetails sx={{ padding: 0 }}>{children}</AccordionDetails>
            </AccordionStyled>
        );
    }),
);
