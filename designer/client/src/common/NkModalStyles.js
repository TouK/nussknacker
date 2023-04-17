/* eslint-disable i18next/no-literal-string */

//fixme remove
class NkModalStyles {
  headerStyles = (fill, color) => {
    return {
      backgroundColor: fill,
      color: color,
    }
  }

}
//TODO this pattern is not necessary, just export every public function as in actions.js
export default new NkModalStyles()
