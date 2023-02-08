//TODO: move all colors to theme
import { css, cx } from "@emotion/css";
import React, { ComponentType, PropsWithChildren } from "react";
import { useRoutes } from "react-router-dom";
import { useNkTheme } from "../../containers/theme";
import styles from "./processTabs.styl";
import { TabLink } from "./TabLink";
import { TabContent } from "./TabContent";

type TabData = { path: string; header: string; Component: ComponentType };

type Props = { tabs: TabData[]; className?: string };

export function Tabs({ tabs, children, className }: PropsWithChildren<Props>) {
    const { theme } = useNkTheme();
    const routes = useRoutes(
        tabs.map(({ path, Component, ...t }) => ({
            path,
            element: (
                <TabContent>
                    <Component />
                </TabContent>
            ),
        })),
    );
    return (
        <div className={cx(styles.tabsRoot, theme.themeClass, css({ backgroundColor: theme.colors.canvasBackground }), className)}>
            <div className={cx(styles.tabsWrap)}>
                {children}
                <div className={cx([styles.tabs, styles.withBottomLine, styles.withDrop, theme.borderRadius && styles.rounded])}>
                    {tabs.map((data) => (
                        <TabLink key={data.path} {...data} />
                    ))}
                </div>
                <div className={styles.contentWrap}>{routes}</div>
            </div>
        </div>
    );
}
