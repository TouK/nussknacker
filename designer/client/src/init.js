//config *has* to be loaded before other imports, see Warning at https://webpack.js.org/guides/public-path/#on-the-fly
import "./config";
import "./styles";
import "symbol-observable"; //redux warning
import { PendingPromise } from "./common/PendingPromise";

window["PendingPromise"] = PendingPromise;

import("./bootstrap");
