const fs = require('fs');
const file = 'web/oscar-viewer/src/lib/data/osh/Node.ts';
let code = fs.readFileSync(file, 'utf8');

code = code.replace(
    /if \(\!this\.auth\?\.username\) await this\.ensureMqttTicket\(\);/g,
    `if (this.auth?.username) { await this.ensureMqttTicket(); }`
);

fs.writeFileSync(file, code);
