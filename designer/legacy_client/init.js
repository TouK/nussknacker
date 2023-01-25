//config *has* to be loaded before other imports, see Warning at https://webpack.js.org/guides/public-path/#on-the-fly
import "./src/config"
import "./styles"
import("./src/bootstrap")
