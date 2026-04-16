import CurveLayer from "osh-js/source/core/ui/layer/CurveLayer";
import ObservationFilter from "osh-js/source/core/consysapi/observation/ObservationFilter";

function toChartTime(rec: any): number | undefined {
  const raw = rec?.timestamp ?? rec?.samplingTime ?? rec?.resultTime ?? rec?.phenomenonTime;
  if (raw == null) return undefined;

  if (raw instanceof Date) {
    return raw.getTime();
  }

  if (typeof raw === "number") {
    // Some live streams publish epoch seconds while others publish epoch millis.
    return raw < 1e12 ? raw * 1000 : raw;
  }

  const parsed = Date.parse(String(raw));
  return Number.isNaN(parsed) ? undefined : parsed;
}

function pickNumber(...values: any[]): number | undefined {
  for (const value of values) {
    if (typeof value === "number" && !Number.isNaN(value)) {
      return value;
    }
  }
  return undefined;
}

export function createNeutronViewCurve(neutronDatasource: { id: any; }) {
  if (!neutronDatasource) return null;

  let nCurve = new CurveLayer({
    dataSourceIds: [neutronDatasource.id],
    getValues: (rec: any) => {
      const x = toChartTime(rec);
      const y = pickNumber(
        rec?.neutronGrossCount,
        rec?.NeutronGrossCount,
        rec?.neutronCount,
        rec?.NeutronCount,
      );
      return x != null && y != null ? {x, y} : undefined;
    },
    name: 'Neutron',
    maxValues: 500,
    borderWidth: 1.5,
    lineColor: '#29b6f6',
    backgroundColor: '#29b6f6',
    xLabel: 'Time',
    yLabel: 'CPS',
    visible: true,
    hidden: false
  });
  return nCurve;
}

export function createThresholdViewCurve(thresholdDatasource: { id: any; }) {
  if (!thresholdDatasource) return null;

  let thresholdCurve = new CurveLayer({
    dataSourceIds: [thresholdDatasource.id],
    getValues: (rec: any) => {
      const x = toChartTime(rec);
      const y = pickNumber(rec?.threshold, rec?.Threshold);
      return x != null && y != null ? {x, y} : undefined;
    },
    name: "Threshold",
    backgroundColor: "rgba(194, 160, 201, 0.3)",
    lineColor: '#9b27b0',
    borderWidth: 1.5,
    visible: true,
    hidden: false,
    xLabel: 'Time',
    yLabel: 'CPS',
    order: 2,
    fill: 1,
    maxValues: 500,
  });
  return thresholdCurve;
}

export function createGammaViewCurve(gammaDatasource: { id: any; }) {
  if (!gammaDatasource) return null;

  let gCurve = new CurveLayer({
    dataSourceIds: [gammaDatasource.id],
    getValues: (rec: any) => {
      const x = toChartTime(rec);
      const y = pickNumber(
        rec?.gammaGrossCount,
        rec?.GammaGrossCount,
        rec?.gammaCount,
        rec?.GammaCount,
      );
      return x != null && y != null ? {x, y} : undefined;
    },
    name: "Gamma",
    xLabel: 'Time',
    yLabel: 'CPS',
    borderWidth: 1.5,
    backgroundColor: "rgba(245, 166, 160, 0.1)",
    lineColor: "#f44336",
    visible: true,
    order: 1,
    fill: 1,
    maxValues: 500,
  });
  return gCurve;
}

/**
 * Patches Chart.js datasets to ensure chronological sorting
 */
export function patchChartSorting(datasets: any[]) {
  return datasets.map(dataset => ({
    ...dataset,
    data: [...dataset.data].sort((a, b) => a.x - b.x)
  }));
}

// get latest gamma background from threshold datasource to calc nsigma for chart export
export function createNSigmaCalcViewCurve(gammaDatasource: any, latestGB: number) {
  if (!gammaDatasource) {
    return null;
  }

  let nCurve = new CurveLayer({
    dataSourceIds: [gammaDatasource.id],
    getValues: (rec: any) => {
      if (latestGB != null) {
        const x = toChartTime(rec);
        const gross = pickNumber(rec?.gammaGrossCount, rec?.GammaGrossCount);
        if (x == null || gross == null) return undefined;
        let nSigmaValue: number = (gross - latestGB) / Math.sqrt(latestGB)
        return {x, y: nSigmaValue}
      }
      return undefined;
    },
    name: "Gamma Nσ",
    borderWidth: 1.5,
    backgroundColor: "rgba(245, 166, 160, 0.1)",
    lineColor: "#f44336",
    xLabel: 'Time',
    yLabel: 'Nσ',
    visible: true,
    hidden: false,
    fill: 1,
    order: 0,
    maxValues: 500,
  });
  return nCurve;
}

export async function getObservations(startTime: any, endTime: any, datastream: any){
  let lastestGammaBackground: number;
  let res = await datastream.searchObservations(new ObservationFilter({resultTime: `${startTime}/${endTime}`}), 1);
  let newObs = await res.nextPage();
  newObs.map((ob: any) =>{
    const lastGB = ob.result.latestGammaBackground;
    if(lastGB != null) lastestGammaBackground = lastGB;
  })
  return lastestGammaBackground;
}

export function createThreshSigmaViewCurve(thresholdDatasource: { id: any; }) {
  if (!thresholdDatasource) return null;

  let gCurve = new CurveLayer({
    dataSourceIds: [thresholdDatasource.id],
    getValues: (rec: any) => {
      const x = toChartTime(rec);
      const y = pickNumber(rec?.nSigma, rec?.NSigma);
      return x != null && y != null ? { x, y } : undefined;
    },
    name: "Threshold",
    xLabel: 'Time',
    yLabel: 'Nσ',
    borderWidth: 1.5,
    backgroundColor: "rgba(194, 160, 201, 0.3)",
    lineColor: '#9b27b0',
    visible: true,
    hidden: false,
    order: 1,
    fill: 1,
    maxValues: 500,
  });
  return gCurve;
}
