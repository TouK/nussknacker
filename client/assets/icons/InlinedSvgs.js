export default class InlinedSvgs {}

//fixme zamienic wszystkie img z svg na te ponizsze? (zeby moc je stylowac w cssach)
//fixme tutaj pewno bedzie trzeba parametryzowac kolor...
InlinedSvgs.nodeCustom = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style> .a { fill: white; } .b { fill: none; } </style> </defs> <title>node-custom</title> <g> <path class="a" d="M17.43,12.77a1.14,1.14,0,0,1-.6,1L10.54,17.2a1.11,1.11,0,0,1-1.09,0L3.17,13.77a1.14,1.14,0,0,1-.6-1V5.91a1.14,1.14,0,0,1,.75-1.07L9.61,2.56a1.12,1.12,0,0,1,.79,0l6.29,2.29a1.14,1.14,0,0,1,.75,1.07ZM16.23,5.9,10,3.63,3.77,5.9,10,8.16Zm.05,6.87V7.09L10.57,9.17v6.71Z"/> <rect class="b" width="20" height="20"/> </g>
</svg>
`
InlinedSvgs.nodeEnricher = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-enricher</title> <g> <path class="a" d="M19,8a.57.57,0,0,1,0,.73l-8.57,9.14a.58.58,0,0,1-.84,0L1,8.77A.57.57,0,0,1,1,8L4.4,3.46a.55.55,0,0,1,.46-.23H15.14a.55.55,0,0,1,.46.23ZM7.48,4.37H5.14L2.57,7.8H5.66Zm.83,10.51L5.63,8.94H2.75Zm4.8-5.94H6.88L10,15.84ZM13,7.8,11.22,4.37H8.78L7,7.8Zm4.21,1.14H14.37l-2.68,5.94Zm.18-1.14L14.86,4.37H12.52L14.34,7.8Z"/> <rect class="b" width="20" height="20"/> </g> </svg>
`

InlinedSvgs.nodeFilter = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-filter</title> <g> <path class="a" d="M16.12,4.69l-4.4,4.4v6.62a.58.58,0,0,1-.35.53.64.64,0,0,1-.22,0,.53.53,0,0,1-.4-.17L8.45,13.83a.57.57,0,0,1-.17-.4V9.09l-4.4-4.4a.56.56,0,0,1-.12-.62.58.58,0,0,1,.53-.35H15.71a.58.58,0,0,1,.53.35A.56.56,0,0,1,16.12,4.69Z"/> <rect class="b" width="20" height="20"/> </g> </svg>
`

InlinedSvgs.nodeService = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-service</title> <g> <path class="a" d="M12.55,10.72a.25.25,0,0,1-.18.24l-1.23.19a3.61,3.61,0,0,1-.25.61c.22.32.46.61.72.92a.28.28,0,0,1,.06.16.21.21,0,0,1-.06.15c-.16.21-1.05,1.19-1.28,1.19a.3.3,0,0,1-.17-.06l-.92-.72a3.4,3.4,0,0,1-.61.25,7.93,7.93,0,0,1-.18,1.23.25.25,0,0,1-.24.19H6.71a.25.25,0,0,1-.24-.2l-.18-1.22a3.88,3.88,0,0,1-.6-.25l-.94.71a.23.23,0,0,1-.16.06.26.26,0,0,1-.17-.06c-.21-.19-1.15-1-1.15-1.27a.27.27,0,0,1,.06-.15c.23-.3.47-.6.7-.91a4.23,4.23,0,0,1-.28-.65l-1.21-.19a.23.23,0,0,1-.19-.23V9.22A.25.25,0,0,1,2.54,9l1.23-.19A3.61,3.61,0,0,1,4,8.18c-.22-.32-.46-.61-.72-.92a.3.3,0,0,1-.06-.16.24.24,0,0,1,.06-.16c.16-.22,1.05-1.18,1.28-1.18a.3.3,0,0,1,.17.06l.92.72a4.12,4.12,0,0,1,.61-.25,8,8,0,0,1,.18-1.23.25.25,0,0,1,.24-.19H8.19a.25.25,0,0,1,.24.2l.18,1.22a4,4,0,0,1,.6.25l.94-.71a.24.24,0,0,1,.16-.06.26.26,0,0,1,.17.06c.21.19,1.15,1.05,1.15,1.27a.27.27,0,0,1-.06.15c-.23.31-.47.6-.69.91a5.23,5.23,0,0,1,.27.65L12.36,9a.25.25,0,0,1,.19.24ZM7.45,7.93a2,2,0,1,0,2,2A2,2,0,0,0,7.45,7.93Zm10.2-1.48c0,.12-1,.23-1.19.25a1.93,1.93,0,0,1-.24.41,7.39,7.39,0,0,1,.41,1.1.06.06,0,0,1,0,.06c-.1.06-.95.57-1,.57a5,5,0,0,1-.78-.92l-.24,0-.24,0a5,5,0,0,1-.78.92s-.89-.51-1-.57a.07.07,0,0,1,0-.06A7.77,7.77,0,0,1,13,7.11a1.93,1.93,0,0,1-.24-.41c-.16,0-1.19-.13-1.19-.25V5.33c0-.12,1-.23,1.19-.25A2.11,2.11,0,0,1,13,4.67a7.74,7.74,0,0,1-.41-1.1.07.07,0,0,1,0-.06c.1,0,.95-.56,1-.56a5,5,0,0,1,.78.91l.24,0,.24,0A7.13,7.13,0,0,1,15.56,3l0,0s.89.5,1,.56a.07.07,0,0,1,0,.06,7.41,7.41,0,0,1-.41,1.1,2.11,2.11,0,0,1,.24.41c.16,0,1.19.13,1.19.25Zm0,8.16c0,.12-1,.23-1.19.25a1.93,1.93,0,0,1-.24.41,7.39,7.39,0,0,1,.41,1.1.06.06,0,0,1,0,.06c-.1.06-.95.57-1,.57a5,5,0,0,1-.78-.92l-.24,0-.24,0a5,5,0,0,1-.78.92s-.89-.51-1-.57a.07.07,0,0,1,0-.06,7.77,7.77,0,0,1,.41-1.1,1.93,1.93,0,0,1-.24-.41c-.16,0-1.19-.13-1.19-.25V13.49c0-.12,1-.23,1.19-.25a2.11,2.11,0,0,1,.24-.41,7.74,7.74,0,0,1-.41-1.1.07.07,0,0,1,0-.06c.1,0,.95-.56,1-.56a5,5,0,0,1,.78.91l.24,0,.24,0a7.13,7.13,0,0,1,.73-.89l0,0s.89.5,1,.56a.07.07,0,0,1,0,.06,7.41,7.41,0,0,1-.41,1.1,2.11,2.11,0,0,1,.24.41c.16,0,1.19.13,1.19.25ZM14.59,4.87a1,1,0,1,0,1,1A1,1,0,0,0,14.59,4.87Zm0,8.16a1,1,0,1,0,1,1A1,1,0,0,0,14.59,13Z"/> <rect class="b" width="20" height="20"/> </g> </svg>`

InlinedSvgs.nodeSink = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-sink</title> <g> <path class="a" d="M10,16.57A4.57,4.57,0,0,1,5.43,12a4.63,4.63,0,0,1,.72-2.46A22,22,0,0,0,9.24,4,.81.81,0,0,1,10,3.43a.8.8,0,0,1,.76.57,22,22,0,0,0,3.09,5.54,4.56,4.56,0,0,1-3.85,7Zm-.18-4A5.68,5.68,0,0,1,9,11.14a.19.19,0,0,0-.37,0,5.68,5.68,0,0,1-.78,1.38,1.22,1.22,0,0,0-.18.62,1.14,1.14,0,1,0,2.29,0A1.22,1.22,0,0,0,9.82,12.53Z"/> <rect class="b" width="20" height="20"/> </g> </svg>`

InlinedSvgs.nodeSource = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-source</title> <g> <path class="a" d="M16.86,4.29V5.43c0,1.26-3.07,2.29-6.86,2.29s-6.86-1-6.86-2.29V4.29C3.14,3,6.21,2,10,2S16.86,3,16.86,4.29Zm0,3.05V8.86c0,1.26-3.07,2.29-6.86,2.29s-6.86-1-6.86-2.29V7.34c1.47,1,4.17,1.52,6.86,1.52S15.38,8.37,16.86,7.34Zm0,3.43v1.52c0,1.26-3.07,2.29-6.86,2.29s-6.86-1-6.86-2.29V10.77c1.47,1,4.17,1.52,6.86,1.52S15.38,11.8,16.86,10.77Zm0,3.43v1.52C16.86,17,13.79,18,10,18s-6.86-1-6.86-2.29V14.2c1.47,1,4.17,1.52,6.86,1.52S15.38,15.23,16.86,14.2Z"/> <rect class="b" width="20" height="20"/> </g> </svg>`

InlinedSvgs.nodeSplit = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-split</title> <g> <path class="a" d="M17.74,12.48h-.93V10.62a1.25,1.25,0,0,0-1.24-1.24h-5V7.52h.93a.93.93,0,0,0,.93-.93V3.5a.93.93,0,0,0-.93-.93H8.45a.93.93,0,0,0-.93.93V6.6a.93.93,0,0,0,.93.93h.93V9.38h-5a1.25,1.25,0,0,0-1.24,1.24v1.86H2.26a.93.93,0,0,0-.93.93v3.1a.93.93,0,0,0,.93.93H5.36a.93.93,0,0,0,.93-.93V13.4a.93.93,0,0,0-.93-.93H4.43V10.62H15.57v1.86h-.93a.93.93,0,0,0-.93.93v3.1a.93.93,0,0,0,.93.93h3.09a.93.93,0,0,0,.93-.93V13.4A.93.93,0,0,0,17.74,12.48Z"/> <rect class="b" width="20" height="20"/> </g> </svg>`


InlinedSvgs.nodeSwitch = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-switch</title> <g> <g> <path class="a" d="M5.58,8.59H.81a.35.35,0,0,0-.35.35v2.12a.35.35,0,0,0,.35.35H5.58a.35.35,0,0,0,.35-.35V8.94A.35.35,0,0,0,5.58,8.59Z"/> <path class="a" d="M19.44,9.71,15.19,5.85a.37.37,0,0,0-.39-.05.33.33,0,0,0-.21.32V8.59H9.93a.35.35,0,0,0-.35.35v2.12a.35.35,0,0,0,.35.35H14.6v2.48a.35.35,0,0,0,.6.25l4.24-3.91a.38.38,0,0,0,.11-.27A.35.35,0,0,0,19.44,9.71Z"/> <rect class="a" x="7.33" y="3.79" width="1.03" height="12.42" rx="0.24" ry="0.24"/> </g> <rect class="b" width="20" height="20"/> </g> </svg>`

InlinedSvgs.nodeVariable = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #fff; } .b { fill: none; } </style> </defs> <title>node-variable</title> <g> <path class="a" d="M16.16,16.59H12.52l-2.34-3.7-.35-.62A.79.79,0,0,1,9.67,12h0a2.71,2.71,0,0,1-.13.31c-.09.18-.22.41-.37.65L6.85,16.59H3.06V14.14H4.94L7.84,9.87l-2.72-4h-2V3.41H7.16l2,3.35c.13.21.23.43.34.62a.8.8,0,0,1,.16.31h0a1,1,0,0,1,.16-.31l.37-.62,2.06-3.35h3.77V5.87H14.27l-2.7,3.92,3,4.35h1.6Z"/> <rect class="b" width="20" height="20"/> </g> </svg>`

InlinedSvgs.svgs = {
  "Source": InlinedSvgs.nodeSource,
  "Sink": InlinedSvgs.nodeSink,
  "Filter": InlinedSvgs.nodeFilter,
  "Switch": InlinedSvgs.nodeSwitch,
  "VariableBuilder": InlinedSvgs.nodeVariable,
  "Variable": InlinedSvgs.nodeVariable,
  "Enricher": InlinedSvgs.nodeEnricher,
  "Split": InlinedSvgs.nodeSplit,
  "Processor": InlinedSvgs.nodeService,
  "CustomNode": InlinedSvgs.nodeCustom
}


InlinedSvgs.buttonAlign = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-align</title> <g> <path class="a" d="M5.5,5.23h21V8.54H5.5ZM7.16,23.46H24.84v3.32H7.16ZM11,17.38H21V20.7H11ZM8.82,11.3H23.18v3.31H8.82Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonDeploy = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-deploy</title> <g> <path class="a" d="M9.31,5.71l13.38,10v.77L9.31,26.29Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonDownload = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-download</title> <g> <path class="a" d="M14.92,16.15H5.08V18h9.85ZM11.23,2H8.77v8L5.69,6.31V10L10,14.92,14.31,10V6.31L11.23,10Z"/> <rect class="b" width="20" height="20"/> </g> </svg>`

InlinedSvgs.buttonExport = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-export</title> <g> <path class="a" d="M14.34,20.49V10.82L9.92,16.07V10.68L16,3.78l6.08,6.91v5.39l-4.42-5.25v9.67ZM4.4,17.73h8.84v2.21H6.61V26H25.39V19.94H18.76V17.73H27.6v10.5H4.4Zm8.84,4.42h5.53V23.8H13.24Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonFromFile = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-from-file</title> <g> <path class="a" d="M9,7.25H23V4.63H9Zm5.25,20.12h3.5V16l4.37,5.25V16L16,9,9.88,16v5.25L14.25,16Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonHide = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-hide</title> <g> <path class="a" d="M16,13.32A2.68,2.68,0,1,1,13.32,16,2.68,2.68,0,0,1,16,13.32Zm0-4.46c6.25,0,11.6,4.46,11.6,7.14S22.25,23.14,16,23.14,4.4,18.68,4.4,16,9.75,8.86,16,8.86Zm0,2.23A4.91,4.91,0,1,0,20.91,16,4.91,4.91,0,0,0,16,11.09ZM8.4,22a17,17,0,0,0,2.16,1L3.13,30.43a19.45,19.45,0,0,1-1.56-1.56ZM23.6,10a17,17,0,0,0-2.16-1l7.43-7.43a19.45,19.45,0,0,1,1.56,1.56Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonImport = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-import</title> <g> <path class="a" d="M14.34,3.85v9.67L9.92,8.27v5.39L16,20.56l6.08-6.91V8.27l-4.42,5.25V3.85ZM4.4,17.66h7.18l2.07,2.21h-7v6.08H25.39V19.87h-7l2.07-2.21H27.6v10.5H4.4Zm8.84,4.42h5.53v1.66H13.24Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonMetrics = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-metrics</title> <g> <path class="a" d="M4.4,5.5H6.05V24.84H27.6V26.5H4.4ZM7.16,23.74V21.11l5.52-5.66,7.18,3.73L26.5,8.82l1.11,1.1-7.18,11.6-7.6-3.87Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonRedo = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-redo</title> <g> <path class="a" d="M23,6.91l2.62,2.62v7h-7L16,13.91h4.89a6.56,6.56,0,1,0-9.53,9L9.2,25.09A9.62,9.62,0,1,1,23,11.68Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonSave = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-save</title> <g> <path class="a" d="M6.38,6.38H25.62V25.62H20.37V19.5H11.63v6.12H6.38Zm7,19.25V23h3.5v2.62ZM9.88,8.13v5.25H22.12V8.13Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonSettings = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-settings</title> <g> <path class="a" d="M16,8a8.06,8.06,0,0,1,1.4.12l1.16-2.86,3.07,1.24L20.47,9.35a8.05,8.05,0,0,1,2.17,2.17l2.86-1.16,1.24,3.07L23.89,14.6a8.1,8.1,0,0,1,0,2.8l2.86,1.16-1.24,3.07-2.86-1.16a8.06,8.06,0,0,1-1.94,2L22,25.31l-3,1.35-1.26-2.82a8.09,8.09,0,0,1-3.07.05l-1.16,2.86-3.07-1.24,1.16-2.86a8.06,8.06,0,0,1-2.17-2.17L6.49,21.63,5.25,18.56,8.11,17.4a8.1,8.1,0,0,1,0-2.8L5.25,13.44l1.24-3.07,2.86,1.16a8.06,8.06,0,0,1,1.94-2L10,6.69l3-1.35,1.26,2.82A8,8,0,0,1,16,8Zm0,2.49A5.53,5.53,0,1,0,21.52,16,5.52,5.52,0,0,0,16,10.47Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonStop = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-stop</title> <g> <rect class="a" x="6.74" y="6.74" width="18.52" height="18.52"/> <rect class="b" width="32" height="32"/> </g> </svg>
`

InlinedSvgs.buttonUndo = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-undo</title> <g> <path class="a" d="M9,6.91v4.77a9.62,9.62,0,1,1,13.8,13.41l-2.17-2.17a6.56,6.56,0,1,0-9.53-9H16l-2.62,2.62h-7v-7Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonUndo_1 = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-undo_1</title> <g> <path class="a" d="M9,6.91v4.77a9.62,9.62,0,1,1,13.8,13.41l-2.17-2.17a6.56,6.56,0,1,0-9.53-9H16l-2.62,2.62h-7v-7Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.buttonUpload_1 = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"> <defs> <style>.a { fill: #999; } .b { fill: none; } </style> </defs> <title>button-upload_1</title> <g> <path class="a" d="M9,24.75H23v-3.5h2.62v6.12H6.38V21.25H9ZM14.25,23h3.5V11.63l4.37,5.25V11.63L16,4.63l-6.12,7v5.25l4.37-5.25Z"/> <rect class="b" width="32" height="32"/> </g> </svg>`

InlinedSvgs.creatorArrow = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 6.56 11.71"> <defs> <style>.a { fill: none; stroke: #b3b3b3; stroke-miterlimit: 10; } </style> </defs> <title>creator-arrow</title> <polyline class="a" points="0.35 11.35 5.85 5.85 0.35 0.35"/> </svg>`

InlinedSvgs.creatorFolder = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"> <defs> <style>.a { fill: #b3b3b3; } .b { fill: none; } </style> </defs> <title>creator-folder</title> <g> <path class="a" d="M0,4.8H16v9.14H0Zm14.17-.91H7.31A3.58,3.58,0,0,1,8.46,2.06H13.6a.68.68,0,0,1,.57.57Z"/> <rect class="b" width="16" height="16"/> </g> </svg>`

InlinedSvgs.tipsInfo = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"> <defs> <style>.a { fill: #b3b3b3; } .b { fill: none; } </style> </defs> <title>tips-info</title> <g> <path class="a" d="M8,0A8,8,0,1,1,0,8,8,8,0,0,1,8,0ZM6,6V7.33H7.33V12H6v1.33h4.67V12H9.33V6ZM8.33,2.67a1,1,0,1,0,1,1A1,1,0,0,0,8.33,2.67Z"/> <rect class="b" width="16" height="16"/> </g> </svg>`

InlinedSvgs.tipsWarning = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"> <defs> <style>.warning { fill: #fbb03b; } .b { fill: none; } </style> </defs> <title>tips-warning</title> <g> <path class="warning" d="M15.49,15.46H.51A1.73,1.73,0,0,1,.51,13L7,1A1.73,1.73,0,0,1,9.43,1l6.07,12A1.73,1.73,0,0,1,15.49,15.46ZM6.56,4.65l.72,5.91H8.72l.72-5.91ZM8,11.42a1.15,1.15,0,1,0,1.15,1.15A1.15,1.15,0,0,0,8,11.42Z"/> <rect class="b" width="16" height="16"/> </g> </svg>`

/*font-awesome to jest wziete z https://github.com/encharm/Font-Awesome-SVG-PNG/tree/master/black/svg*/
InlinedSvgs.buttonDelete = `<svg viewBox="0 0 1792 1792" xmlns="http://www.w3.org/2000/svg"><defs> <style>.a {fill: #999}</style> </defs><path class="a" d="M1490 1322q0 40-28 68l-136 136q-28 28-68 28t-68-28l-294-294-294 294q-28 28-68 28t-68-28l-136-136q-28-28-28-68t28-68l294-294-294-294q-28-28-28-68t28-68l136-136q28-28 68-28t68 28l294 294 294-294q28-28 68-28t68 28l136 136q28 28 28 68t-28 68l-294 294 294 294q28 28 28 68z"/><rect class="b" width="32" height="32"/></svg>`
