const fs = require('fs');
const file = 'web/oscar-viewer/node_modules/osh-js/core/datasource/common/handler/TimeSeries.handler.js';
let content = fs.readFileSync(file, 'utf-8');

content = content.replace(/version:\s*data\[i\]\.version/g, 'version: data[i] ? data[i].version : undefined');
content = content.replace(/version:\s*data\[0\]\.version/g, 'version: data[0] ? data[0].version : undefined');
content = content.replace(/data\[0\]\.version\s*!==\s*this\.properties\.version/g, '(data[0] && data[0].version !== this.properties.version)');

fs.writeFileSync(file, content);
