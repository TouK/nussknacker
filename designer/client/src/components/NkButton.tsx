import cn from "classnames";
import React from "react";
import styles from "../stylesheets/graph.styl";
import { ButtonProps, ButtonWithFocus } from "./withFocus";

export function NkButton({ className, ...props }: ButtonProps) {
    return <ButtonWithFocus {...props} className={cn(styles.espButton, className)} />;
}
