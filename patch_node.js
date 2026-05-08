const fs = require('fs');
const file = 'web/oscar-viewer/src/lib/data/osh/Node.ts';
let code = fs.readFileSync(file, 'utf8');

const fetchStreamsStr = `
        const dataStreamCollection = await this.getDataStreamsApi()
            .searchDataStreams(
                new DataStreamFilter({
                    system: laneIds.join(","),
                    validTime: "latest"
                }), 1000);

        const allDataStreams = [];
        while (dataStreamCollection.hasNext()) {
            const dataStreams = await dataStreamCollection.nextPage();
            allDataStreams.push(...dataStreams);
        }
`;

const fetchStreamsPatch = `
        const allDataStreams = [];
        // Chunk laneIds to prevent exceeding URI length limits (e.g. 10 at a time)
        for (let i = 0; i < laneIds.length; i += 10) {
            const chunk = laneIds.slice(i, i + 10);
            const dataStreamCollection = await this.getDataStreamsApi()
                .searchDataStreams(
                    new DataStreamFilter({
                        system: chunk.join(","),
                        validTime: "latest"
                    }), 1000);

            while (dataStreamCollection.hasNext()) {
                const dataStreams = await dataStreamCollection.nextPage();
                allDataStreams.push(...dataStreams);
            }
        }
`;

const fetchControlStr = `
        const controlStreamCollection = await this.getControlStreamApi()
            .searchControlStreams(
                new ControlStreamFilter({
                    system: laneIds.join(","),
                    validTime: "latest"
                }), 1000);

        const allControlStreams = [];
        while (controlStreamCollection.hasNext()) {
            const controlStreams = await controlStreamCollection.nextPage();
            allControlStreams.push(...controlStreams);
        }
`;

const fetchControlPatch = `
        const allControlStreams = [];
        for (let i = 0; i < laneIds.length; i += 10) {
            const chunk = laneIds.slice(i, i + 10);
            const controlStreamCollection = await this.getControlStreamApi()
                .searchControlStreams(
                    new ControlStreamFilter({
                        system: chunk.join(","),
                        validTime: "latest"
                    }), 1000);

            while (controlStreamCollection.hasNext()) {
                const controlStreams = await controlStreamCollection.nextPage();
                allControlStreams.push(...controlStreams);
            }
        }
`;

code = code.replace(fetchStreamsStr, fetchStreamsPatch);
code = code.replace(fetchControlStr, fetchControlPatch);

fs.writeFileSync(file, code);
