//TODO: move all colors to theme
import { css, cx } from "@emotion/css";
import React, { ComponentType, PropsWithChildren } from "react";
import { Routes } from "react-router-dom";
import { useNkTheme } from "../../containers/theme";
import styles from "./processTabs.styl";
import { TabLink } from "./TabLink";
import { Route } from "react-router";
import { TabContent } from "./TabContent";

type TabData = { path: string; header: string; Component: ComponentType };

type Props = { tabs: TabData[]; className?: string };

export function Tabs({ tabs, children, className }: PropsWithChildren<Props>) {
    const { theme } = useNkTheme();
    return (
        <div className={cx(styles.tabsRoot, theme.themeClass, css({ backgroundColor: theme.colors.canvasBackground }), className)}>
            <div className={cx(styles.tabsWrap)}>
                {children}
                <div className={cx([styles.tabs, styles.withBottomLine, styles.withDrop, theme.borderRadius && styles.rounded])}>
                    {tabs.map((data) => (
                        <TabLink key={data.path} {...data} />
                    ))}
                </div>
                <div className={styles.contentWrap}>
                    <Routes>
                        {tabs.map(({ path, Component }) => (
                            <Route
                                key={path}
                                path={path}
                                element={
                                    <TabContent>
                                        <Component />
                                    </TabContent>
                                }
                            ></Route>
                        ))}
                    </Routes>
                </div>
            </div>
        </div>
    );
}
