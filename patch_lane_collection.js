const fs = require('fs');
const file = 'web/oscar-viewer/src/lib/data/oscar/LaneCollection.ts';
let code = fs.readFileSync(file, 'utf8');

code = code.replace(
    /let hasProp = ds.properties.observedProperties.some\(\(prop: any\) => prop.definition === obsProperty\)/g,
    `let hasProp = ds.properties.observedProperties.some((prop: any) => prop.definition.includes(obsProperty))`
);

fs.writeFileSync(file, code);
