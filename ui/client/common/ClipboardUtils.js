/* eslint-disable i18next/no-literal-string */
const readText = event => (event.clipboardData || window.clipboardData).getData("text")

// We could have used navigator.clipboard.writeText but it is not defined for
// content delivered via HTTP. The workaround is to create a hidden element
// and then write text into it. After that copy command is used to replace
// clipboard's contents with given text. What is more the hidden element is
// assigned with given id to be able to differentiate between artificial
// copy event and the real one triggered by user.
// Based on https://techoverflow.net/2018/03/30/copying-strings-to-the-clipboard-using-pure-javascript/
const writeText = (text, elementId) => {
  const el = document.createElement("textarea")
  el.value = text
  el.setAttribute("id", elementId)
  el.setAttribute("readonly", "")
  el.style = {position: "absolute", left: "-9999px"}
  document.body.appendChild(el)
  el.select()
  document.execCommand("copy")
  document.body.removeChild(el)
}

export default {
  readText,
  writeText,
}
