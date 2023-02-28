import React, { PropsWithChildren } from "react";
import styles from "./processTabs.styl";

export function TabContent({ children }: PropsWithChildren<unknown>) {
    return <div className={styles.content}>{children}</div>;
}
