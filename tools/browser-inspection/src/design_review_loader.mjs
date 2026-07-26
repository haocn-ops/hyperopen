import path from "node:path";
import { fileURLToPath } from "node:url";
import { readJsonFile } from "./util.mjs";
import {
  assertDesignReviewConfig,
  assertDesignReviewRouting
} from "./design_review_contracts.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const defaultConfigPath = path.resolve(__dirname, "../config/design-review-defaults.json");
const defaultRoutingPath = path.resolve(__dirname, "../config/design-review-routing.json");

function normalizedPath(filePath) {
  return String(filePath || "").replaceAll(path.sep, "/");
}

function globToRegex(glob) {
  const escaped = normalizedPath(glob).replace(/[|\\{}()[\]^$+?.]/g, "\\$&");
  const withDoubleStar = escaped.replaceAll("**", "\u0000");
  const withSingleStar = withDoubleStar.replaceAll("*", "[^/]*");
  return new RegExp(`^${withSingleStar.replaceAll("\u0000", ".*")}$`);
}

function pathMatchesGlob(filePath, glob) {
  return globToRegex(glob).test(normalizedPath(filePath));
}

export async function loadDesignReviewConfig(configPath = defaultConfigPath) {
  const value = await readJsonFile(configPath, {});
  assertDesignReviewConfig(value);
  return value;
}

export async function loadDesignReviewRouting(routingPath = defaultRoutingPath) {
  const value = await readJsonFile(routingPath, {});
  assertDesignReviewRouting(value);
  return value;
}

export function selectDesignReviewTargetsForChangedFiles(changedFiles, routing) {
  const files = (changedFiles || []).map(normalizedPath).filter(Boolean);
  const targetMap = new Map((routing.targets || []).map((target) => [target.id, target]));
  const selectedIds = new Set(routing.defaultTargets || []);
  const matchedRuleIds = new Set();

  for (const filePath of files) {
    for (const rule of routing.rules || []) {
      if (pathMatchesGlob(filePath, rule.glob)) {
        matchedRuleIds.add(rule.id);
        for (const targetId of rule.targets || []) {
          if (targetMap.has(targetId)) {
            selectedIds.add(targetId);
          }
        }
      }
    }
  }

  const targets = [...selectedIds]
    .map((targetId) => targetMap.get(targetId))
    .filter(Boolean)
    .sort((left, right) => left.id.localeCompare(right.id));

  return {
    changedFiles: files,
    matchedRuleIds: [...matchedRuleIds].sort(),
    targets
  };
}

export async function resolveDesignReviewSelection(options = {}) {
  const routing = await loadDesignReviewRouting(options.routingPath);
  const selection = selectDesignReviewTargetsForChangedFiles(options.changedFiles || [], routing);

  if (Array.isArray(options.targetIds) && options.targetIds.length > 0) {
    const targetMap = new Map((routing.targets || []).map((target) => [target.id, target]));
    const requested = options.targetIds.map((value) => String(value));
    const missing = requested.filter((targetId) => !targetMap.has(targetId));
    if (missing.length > 0) {
      throw new Error(`Unknown design-review target id(s): ${missing.join(", ")}`);
    }
    selection.targets = requested.map((targetId) => targetMap.get(targetId)).filter(Boolean);
  }

  return selection;
}
