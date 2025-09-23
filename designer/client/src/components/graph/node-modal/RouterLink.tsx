import type { PropsWithChildren } from "react";
import React from "react";
import { Link } from "react-router-dom";

const RouterLink = ({ to, children }: PropsWithChildren<{ to: string }>) => <Link to={to}>{children}</Link>;

export default RouterLink;
