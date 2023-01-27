import {createBrowserHistory} from "history"
import {BASE_PATH} from "./config"

export default createBrowserHistory({basename: BASE_PATH})
