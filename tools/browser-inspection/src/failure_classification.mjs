import { safeNowIso } from "./util.mjs";

const SIGNATURES = [
  {
    reason: "loopback_bind_blocked",
    summary: "Loopback socket bind is blocked in this environment.",
    regexes: [/listen\s+EPERM/i, /socketexception:.*operation not permitted/i, /operation not permitted/i]
  },
  {
    reason: "local_app_unreachable",
    summary: "Local app is not reachable at the expected URL.",
    regexes: [/timed out waiting for local app/i, /econnrefused/i, /fetch failed/i]
  },
  {
    reason: "attach_endpoint_unreachable",
    summary: "Attach-mode Chrome debug endpoint is unreachable.",
    regexes: [/attach endpoint/i, /devtools endpoint/i, /websocket endpoint/i]
  },
  {
    reason: "chrome_startup_failed",
    summary: "Chrome startup failed before capture could begin.",
    regexes: [/failed to launch chrome/i, /chrome not found/i, /failed to connect to chrome/i]
  }
];

function normalizeMessage(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

export function classifyErrorMessage(message) {
  const normalized = normalizeMessage(message);
  if (!normalized) {
    return null;
  }

  for (const signature of SIGNATURES) {
    if (signature.regexes.some((regex) => regex.test(normalized))) {
      return {
        classification: "automation-gap",
        reason: signature.reason,
        summary: signature.summary,
        message: normalized
      };
    }
  }

  return null;
}

function flattenMessages(input) {
  if (!input) {
    return [];
  }
  if (typeof input === "string") {
    return [input];
  }
  if (Array.isArray(input)) {
    return input.flatMap((entry) => flattenMessages(entry));
  }
  if (typeof input === "object") {
    return Object.values(input).flatMap((entry) => flattenMessages(entry));
  }
  return [String(input)];
}

export function classifyNightlyFailure({ preflight = null, attempts = [], findings = [] } = {}) {
  const findingsList = Array.isArray(findings) ? findings : [];
  const attemptsList = Array.isArray(attempts) ? attempts : [];
  const preflightMessages = flattenMessages(preflight?.checks || []).flatMap((value) =>
    typeof value === "object"
      ? [value.errorMessage, value.message].filter(Boolean)
      : [value]
  );
  const attemptMessages = flattenMessages(
    attemptsList.map((attempt) => [attempt.error, attempt.errorMessage, attempt.logSummary])
  );

  const classified = [...preflightMessages, ...attemptMessages]
    .map((message) => classifyErrorMessage(message))
    .filter(Boolean);

  if (classified.length > 0) {
    const primary = classified[0];
    return {
      classification: "automation-gap",
      reason: primary.reason,
      summary: primary.summary,
      evidence: classified.map((entry) => entry.message).filter(Boolean),
      generatedAt: safeNowIso()
    };
  }

  const failedAttempts = attemptsList.filter((attempt) => attempt.status && attempt.status !== "ok");
  if (failedAttempts.length > 0) {
    return {
      classification: "product-regression",
      reason: "capture_failures_without_infra_signature",
      summary: "One or more captures failed without a known infrastructure signature.",
      evidence: failedAttempts.map((attempt) => attempt.errorMessage || attempt.error || attempt.key || "capture failed"),
      generatedAt: safeNowIso()
    };
  }

  if (findingsList.length > 0) {
    return {
      classification: "product-regression",
      reason: "ui_findings_present",
      summary: "Run completed with UI/runtime findings that need product follow-up.",
      evidence: findingsList.map((entry) => String(entry)),
      generatedAt: safeNowIso()
    };
  }

  return {
    classification: "pass",
    reason: "no_critical_failures_detected",
    summary: "Run completed without classified automation or product regression failures.",
    evidence: [],
    generatedAt: safeNowIso()
  };
}

export function remediationForClassification(classification) {
  if (!classification || classification.classification !== "automation-gap") {
    return null;
  }

  switch (classification.reason) {
    case "loopback_bind_blocked":
      return "Local bind is blocked; run with fallback attach mode: --attach-port <port> --target-id <target-id>.";
    case "attach_endpoint_unreachable":
      return "Start a trusted Chrome with --remote-debugging-port and retry with --attach-port.";
    case "local_app_unreachable":
      return "Ensure local app is running (npm run dev) or use --manage-local-app in a permitted environment.";
    default:
      return "Inspect run logs for infrastructure errors and retry with a known-good local or attach environment.";
  }
}
