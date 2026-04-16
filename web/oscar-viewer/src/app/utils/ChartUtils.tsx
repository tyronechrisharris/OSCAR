import CurveLayer from "osh-js/source/core/ui/layer/CurveLayer";
import ObservationFilter from "osh-js/source/core/consysapi/observation/ObservationFilter";

function pickDefined<T>(...values: T[]): T | undefined {
  for (const value of values) {
    if (value !== undefined && value !== null) {
      return value;
    }
  }
  return undefined;
}

function unwrapTimeValue(raw: any): any {
  if (Array.isArray(raw)) {
    return raw.length > 0 ? raw[raw.length - 1] : undefined;
  }

  if (raw && typeof raw === "object") {
    return pickDefined(
      raw.instant,
      raw.dateTime,
      raw.time,
      raw.value,
      raw.end,
      raw.begin,
    );
  }

  return raw;
}

function toChartTime(rec: any): number | undefined {
  const raw = pickDefined(
    rec?.timestamp,
    rec?.samplingTime,
    rec?.resultTime,
    rec?.phenomenonTime,
    rec?.time,
    rec?.result?.timestamp,
    rec?.result?.samplingTime,
    rec?.result?.resultTime,
    rec?.result?.phenomenonTime,
    rec?.result?.time,
  );

  const unwrapped = unwrapTimeValue(raw);
  if (unwrapped == null) return undefined;

  if (unwrapped instanceof Date) {
    return unwrapped.getTime();
  }

  if (typeof unwrapped === "number") {
    return unwrapped < 1e12 ? unwrapped * 1000 : unwrapped;
  }

  const parsed = Date.parse(String(unwrapped));
  return Number.isNaN(parsed) ? undefined : parsed;
}

function toNumber(value: any): number | undefined {
  if (typeof value === "number" && !Number.isNaN(value)) {
    return value;
  }

  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    if (!Number.isNaN(parsed)) {
      return parsed;
    }
  }

  return undefined;
}

function pickNumber(...values: any[]): number | undefined {
  for (const value of values) {
    const num = toNumber(value);
    if (num !== undefined) {
      return num;
    }
  }
  return undefined;
}

function pickObservationNumber(rec: any, ...names: string[]): number | undefined {
  const candidates: any[] = [];

  for (const name of names) {
    candidates.push(
      rec?.[name],
      rec?.result?.[name],
      rec?.values?.[name],
      rec?.result?.values?.[name],
    );
  }

  return pickNumber(...candidates);
}

export function createNeutronViewCurve(neutronDatasource: { id: any; }) {
  if (!neutronDatasource) return null;

  let nCurve = new CurveLayer({
    dataSourceIds: [neutronDatasource.id],
    getValues: (rec: any) => {
      const x = toChartTime(rec);
      const y = pickObservationNumber(
        rec,
        "neutronGrossCount",
        "NeutronGrossCount",
        "neutronCount",
        "NeutronCount",
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
      const y = pickObservationNumber(rec, "threshold", "Threshold");
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
      const y = pickObservationNumber(
        rec,
        "gammaGrossCount",
        "GammaGrossCount",
        "gammaCount",
        "GammaCount",
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
      if (latestGB == null || latestGB <= 0) {
        return undefined;
      }

      const x = toChartTime(rec);
      const gross = pickObservationNumber(rec, "gammaGrossCount", "GammaGrossCount");
      if (x == null || gross == null) return undefined;

      let nSigmaValue: number = (gross - latestGB) / Math.sqrt(latestGB);
      return {x, y: nSigmaValue};
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

export async function getObservations(startTime: any, endTime: any, datastream: any) {
  let latestGammaBackground: number | undefined;
  let res = await datastream.searchObservations(new ObservationFilter({
    resultTime: `${startTime}/${endTime}`,
  }), 1);
  let newObs = await res.nextPage();

  newObs.map((ob: any) => {
    const lastGB = pickObservationNumber(ob, 'latestGammaBackground', 'LatestGammaBackground');
    if (lastGB != null) {
      latestGammaBackground = lastGB;
    }
  });

  return latestGammaBackground;
}

export function createThreshSigmaViewCurve(thresholdDatasource: { id: any; }) {
  if (!thresholdDatasource) return null;

  let gCurve = new CurveLayer({
    dataSourceIds: [thresholdDatasource.id],
    getValues: (rec: any) => {
      const x = toChartTime(rec);
      const y = pickObservationNumber(rec, 'nSigma', 'NSigma');
      return x != null && y != null ? {x, y} : undefined;
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
