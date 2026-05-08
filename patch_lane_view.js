const fs = require('fs');
const file = 'web/oscar-viewer/src/app/lane-view/page.tsx';
let code = fs.readFileSync(file, 'utf8');

code = code.replace(
    /<Grid item sx=\{\{ width: "100%", height: 800, display: toggleView === 'occupancy' \? 'block' : 'none' \}\}>\s*<EventTable tableMode=\{'lanelog'\} laneMap=\{laneMap\} viewLane viewAdjudicated currentLane=\{currentLane\}\/>\s*<\/Grid>\s*<Grid item sx=\{\{ width: "100%", height: 800, display: toggleView === 'fault' \? 'block' : 'none' \}\}>\s*\{entry && \(\s*<StatusTable currentLane=\{currentLane\} entry=\{entry\} \/>\s*\)\}\s*<\/Grid>/m,
    `{toggleView === 'occupancy' && (
                                    <Grid item sx={{ width: "100%", height: 800 }}>
                                        <EventTable tableMode={'lanelog'} laneMap={laneMap} viewLane viewAdjudicated currentLane={currentLane}/>
                                    </Grid>
                                )}
                                {toggleView === 'fault' && (
                                    <Grid item sx={{ width: "100%", height: 800 }}>
                                        {entry && (
                                            <StatusTable currentLane={currentLane} entry={entry} />
                                        )}
                                    </Grid>
                                )}`
);

fs.writeFileSync(file, code);
