const fs = require('fs');
const file = 'web/oscar-viewer/src/app/lane-view/page.tsx';
let code = fs.readFileSync(file, 'utf8');
console.log(code.indexOf("toggleView === 'occupancy'"));
