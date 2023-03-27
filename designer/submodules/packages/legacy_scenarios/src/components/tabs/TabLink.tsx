import { NavLink } from "react-router-dom";
import React from "react";
import { Tab } from "./Tab";
import styles from "./processTabs.styl";
import { cx } from "@emotion/css";

export function TabLink({ path, header }: { path: string; header: string }) {
    return (
        <NavLink end to={path} className={({ isActive }) => cx(styles.link, isActive && styles.active)}>
            <Tab title={header} />
        </NavLink>
    );
}
