const readText = event => (event.clipboardData || window.clipboardData).getData('text')

const writeText = (text, elementId) => {
  const el = document.createElement('textarea');
  el.value = text;
  el.setAttribute("id", elementId)
  el.setAttribute('readonly', '');
  el.style = {position: 'absolute', left: '-9999px'};
  document.body.appendChild(el);
  el.select();
  document.execCommand('copy');
  document.body.removeChild(el);
}

export default {
  readText,
  writeText,
}