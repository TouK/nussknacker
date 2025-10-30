import type { SlideProps } from "@mui/material";
import { Slide } from "@mui/material";
import React from "react";
import { Transition } from "react-transition-group";
import type { TransitionProps } from "react-transition-group/Transition";

interface DoubleTransitionProps extends Omit<SlideProps, "in" | "direction"> {
    in?: boolean;
    directionIn?: SlideProps["direction"];
    directionOut?: SlideProps["direction"];
    timeout?: TransitionProps["timeout"];
    children: React.ReactElement;
}

export const DoubleTransition: React.FC<DoubleTransitionProps> = ({
    in: inProp,
    directionIn = "left",
    directionOut = "right",
    timeout = 300,
    children,
    ...rest
}) => (
    <Transition in={inProp} timeout={timeout}>
        {(state) => (
            <Slide
                {...rest}
                in={state === "entering" || state === "entered"}
                direction={state === "exiting" || state === "exited" ? directionOut : directionIn}
                timeout={timeout}
            >
                {children}
            </Slide>
        )}
    </Transition>
);
