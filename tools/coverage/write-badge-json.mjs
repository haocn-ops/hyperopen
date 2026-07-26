import fs from "node:fs/promises";
import path from "node:path";

const COVERAGE_SUMMARY_PATH =
  process.env.COVERAGE_SUMMARY_PATH ?? "coverage/coverage-summary.json";
const COVERAGE_BADGE_JSON_PATH =
  process.env.COVERAGE_BADGE_PATH ?? ".github/badges/coverage.json";
const COVERAGE_BADGE_SVG_PATH =
  process.env.COVERAGE_BADGE_SVG_PATH ?? ".github/badges/coverage.svg";

const COLOR_HEX_BY_NAME = {
  brightgreen: "#4c1",
  green: "#97ca00",
  yellowgreen: "#a4a61d",
  yellow: "#dfb317",
  orange: "#fe7d37",
  red: "#e05d44",
};

function coverageColor(percent) {
  if (percent >= 90) return "brightgreen";
  if (percent >= 80) return "green";
  if (percent >= 70) return "yellowgreen";
  if (percent >= 60) return "yellow";
  if (percent >= 50) return "orange";
  return "red";
}

function escapeXml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

function badgeWidth(text, { minWidth, padding }) {
  return Math.max(minWidth, text.length * 10 + padding);
}

function renderSvgBadge({ label, message, color }) {
  const displayLabel = label.toUpperCase();
  const displayMessage = message.toUpperCase();
  const labelWidth = badgeWidth(displayLabel, { minWidth: 62, padding: 9 });
  const messageWidth = badgeWidth(displayMessage, {
    minWidth: 57,
    padding: 16,
  });
  const totalWidth = labelWidth + messageWidth;
  const colorHex = COLOR_HEX_BY_NAME[color] ?? COLOR_HEX_BY_NAME.red;
  const safeLabel = escapeXml(displayLabel);
  const safeMessage = escapeXml(displayMessage);
  const safeAriaLabel = escapeXml(`${displayLabel}: ${displayMessage}`);

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${totalWidth}" height="28" role="img" aria-label="${safeAriaLabel}">
  <title>${safeAriaLabel}</title>
  <g shape-rendering="crispEdges">
    <rect width="${labelWidth}" height="28" fill="#555"/>
    <rect x="${labelWidth}" width="${messageWidth}" height="28" fill="${colorHex}"/>
  </g>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="100">
    <text transform="scale(.1)" x="${labelWidth * 5}" y="175">${safeLabel}</text>
    <text transform="scale(.1)" x="${(labelWidth + messageWidth / 2) * 10}" y="175" font-weight="bold">${safeMessage}</text>
  </g>
</svg>
`;
}

async function readBadgePayloadFromJson(jsonPath) {
  const rawPayload = await fs.readFile(jsonPath, "utf8");
  const payload = JSON.parse(rawPayload);

  if (
    typeof payload?.label !== "string" ||
    typeof payload?.message !== "string" ||
    typeof payload?.color !== "string"
  ) {
    throw new Error(`Expected label/message/color fields in ${jsonPath}`);
  }

  return payload;
}

async function buildCoverageBadgePayload() {
  try {
    const rawSummary = await fs.readFile(COVERAGE_SUMMARY_PATH, "utf8");
    const summary = JSON.parse(rawSummary);
    const lineCoverage = summary?.total?.lines?.pct;

    if (typeof lineCoverage !== "number" || Number.isNaN(lineCoverage)) {
      throw new Error(
        `Expected numeric total.lines.pct in ${COVERAGE_SUMMARY_PATH}`,
      );
    }

    return {
      schemaVersion: 1,
      label: "coverage",
      message: `${lineCoverage.toFixed(2)}%`,
      color: coverageColor(lineCoverage),
    };
  } catch (error) {
    if (error?.code !== "ENOENT") {
      throw error;
    }

    return readBadgePayloadFromJson(COVERAGE_BADGE_JSON_PATH);
  }
}

async function main() {
  const badgePayload = await buildCoverageBadgePayload();
  const badgeSvg = renderSvgBadge(badgePayload);

  await fs.mkdir(path.dirname(COVERAGE_BADGE_JSON_PATH), { recursive: true });
  await fs.mkdir(path.dirname(COVERAGE_BADGE_SVG_PATH), { recursive: true });
  await fs.writeFile(
    COVERAGE_BADGE_JSON_PATH,
    `${JSON.stringify(badgePayload, null, 2)}\n`,
    "utf8",
  );
  await fs.writeFile(COVERAGE_BADGE_SVG_PATH, badgeSvg, "utf8");

  console.log(
    `Wrote coverage badge payload (${badgePayload.message}) to ${COVERAGE_BADGE_JSON_PATH} and ${COVERAGE_BADGE_SVG_PATH}`,
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
