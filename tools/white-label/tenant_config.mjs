import crypto from "node:crypto";

const ROOT_FIELDS = new Set([
  "tenant/id",
  "brand/name",
  "brand/logo-url",
  "theme/id",
  "features",
  "venue",
  "affiliate",
]);
const FEATURE_FIELDS = new Set(["terminal", "analytics", "affiliate"]);
const VENUE_FIELDS = new Set(["id", "label", "url"]);
const AFFILIATE_FIELDS = new Set([
  "provider",
  "id",
  "status",
  "referral-url",
  "event-endpoint",
  "disclosure",
]);
const THEMES = new Set(["dark", "institutional", "hyperdegen"]);
const AFFILIATE_STATUSES = new Set(["configured", "enabled", "disabled", "unavailable"]);
const SECRET_KEY_PATTERN =
  /(?:secret|private[-_ ]?key|seed[-_ ]?phrase|access[-_ ]?token|api[-_ ]?key|api[-_ ]?secret|password|credential|authorization|mnemonic|raw[-_ ]?signature)/i;
const SECRET_VALUE_PATTERN =
  /(?:secret|sk_(?:live|test)_[A-Za-z0-9_-]+|0x[0-9a-f]{32,}|(?:seed|private)[-_ ]?(?:phrase|key)|access[-_ ]?token|api[-_ ]?(?:key|secret)|password|credential|raw[-_ ]?signature)/i;

function fieldError(field, message) {
  throw new Error(`${field}: ${message}`);
}

function parseJsonWithDuplicateKeys(rawText) {
  if (typeof rawText !== "string") {
    throw new Error("JSON input must be UTF-8 text.");
  }

  let index = 0;
  const text = rawText;

  function skipWhitespace() {
    while (/\s/.test(text[index] || "")) {
      index += 1;
    }
  }

  function parseString() {
    const start = index;
    if (text[index] !== '"') {
      throw new Error("Invalid JSON string.");
    }
    index += 1;
    let escaped = false;
    while (index < text.length) {
      const character = text[index];
      if (escaped) {
        escaped = false;
        index += 1;
        continue;
      }
      if (character === "\\") {
        escaped = true;
        index += 1;
        continue;
      }
      if (character === '"') {
        index += 1;
        try {
          return JSON.parse(text.slice(start, index));
        } catch (_error) {
          throw new Error("Invalid JSON string.");
        }
      }
      if (character.charCodeAt(0) < 0x20) {
        throw new Error("Invalid JSON string.");
      }
      index += 1;
    }
    throw new Error("Invalid JSON string.");
  }

  function parseNumber() {
    const match = text.slice(index).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
    if (!match) {
      throw new Error("Invalid JSON number.");
    }
    index += match[0].length;
    return Number(match[0]);
  }

  function parseArray(pathLabel) {
    const values = [];
    index += 1;
    skipWhitespace();
    if (text[index] === "]") {
      index += 1;
      return values;
    }
    while (true) {
      values.push(parseValue(pathLabel));
      skipWhitespace();
      if (text[index] === "]") {
        index += 1;
        return values;
      }
      if (text[index] !== ",") {
        throw new Error("Invalid JSON array.");
      }
      index += 1;
      skipWhitespace();
    }
  }

  function parseObject(pathLabel) {
    const value = {};
    const keys = new Set();
    index += 1;
    skipWhitespace();
    if (text[index] === "}") {
      index += 1;
      return value;
    }
    while (true) {
      const key = parseString();
      if (keys.has(key)) {
        const safeKey = SECRET_KEY_PATTERN.test(key) ? "secret-shaped field" : key;
        throw new Error(`Duplicate JSON key at ${pathLabel}.${safeKey}`);
      }
      keys.add(key);
      skipWhitespace();
      if (text[index] !== ":") {
        throw new Error("Invalid JSON object.");
      }
      index += 1;
      skipWhitespace();
      value[key] = parseValue(`${pathLabel}.${key}`);
      skipWhitespace();
      if (text[index] === "}") {
        index += 1;
        return value;
      }
      if (text[index] !== ",") {
        throw new Error("Invalid JSON object.");
      }
      index += 1;
      skipWhitespace();
    }
  }

  function parseValue(pathLabel) {
    skipWhitespace();
    const character = text[index];
    if (character === '"') return parseString();
    if (character === "{") return parseObject(pathLabel);
    if (character === "[") return parseArray(pathLabel);
    if (character === "-" || /\d/.test(character || "")) return parseNumber();
    for (const [literal, value] of [["true", true], ["false", false], ["null", null]]) {
      if (text.startsWith(literal, index)) {
        index += literal.length;
        return value;
      }
    }
    throw new Error("Invalid JSON value.");
  }

  try {
    const parsed = parseValue("root");
    skipWhitespace();
    if (index !== text.length) {
      throw new Error("Invalid JSON trailing content.");
    }
    return parsed;
  } catch (error) {
    if (error.message.startsWith("Duplicate JSON key")) {
      throw error;
    }
    throw new Error("Invalid JSON input.");
  }
}

function assertExactObject(value, fields, field) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    fieldError(field, "must be an object.");
  }
  for (const key of Object.keys(value)) {
    if (SECRET_KEY_PATTERN.test(key)) {
      fieldError(field, "contains a secret-shaped field.");
    }
    if (!fields.has(key)) {
      fieldError(`${field}.${key}`, "is unknown.");
    }
  }
  for (const key of fields) {
    if (!Object.hasOwn(value, key)) {
      fieldError(`${field}.${key}`, "is required.");
    }
  }
  return value;
}

function requiredString(value, field) {
  if (typeof value !== "string" || value.trim().length === 0) {
    fieldError(field, "must be a non-empty string.");
  }
  const normalized = value.trim();
  if (SECRET_VALUE_PATTERN.test(normalized)) {
    fieldError(field, "contains a secret-shaped value.");
  }
  return normalized;
}

function optionalPublicUrl(value, field) {
  if (typeof value !== "string") {
    fieldError(field, "must be a string.");
  }
  const normalized = value.trim();
  if (!normalized) {
    return "";
  }
  if (SECRET_VALUE_PATTERN.test(normalized)) {
    fieldError(field, "contains a secret-shaped value.");
  }
  let parsed;
  try {
    parsed = new URL(normalized);
  } catch (_error) {
    fieldError(field, "must be an HTTPS URL.");
  }
  if (
    parsed.protocol !== "https:" ||
    !parsed.hostname ||
    parsed.username ||
    parsed.password ||
    parsed.hash
  ) {
    fieldError(field, "must be a credential-free HTTPS URL without a fragment.");
  }
  return normalized;
}

function requiredBoolean(value, field) {
  if (typeof value !== "boolean") {
    fieldError(field, "must be a boolean.");
  }
  return value;
}

function assertNoSecretValues(value, field = "root") {
  if (typeof value === "string" && SECRET_VALUE_PATTERN.test(value)) {
    fieldError(field, "contains a secret-shaped value.");
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoSecretValues(item, `${field}.${index}`));
  } else if (value && typeof value === "object") {
    for (const [key, nested] of Object.entries(value)) {
      if (SECRET_KEY_PATTERN.test(key)) {
        fieldError(field, "contains a secret-shaped field.");
      }
      assertNoSecretValues(nested, `${field}.${key}`);
    }
  }
}

export function normalizeWhiteLabelOrigin(value) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error("canonical origin must be a non-empty HTTPS origin.");
  }
  let parsed;
  try {
    parsed = new URL(value.trim());
  } catch (_error) {
    throw new Error("canonical origin must be an HTTPS origin.");
  }
  if (
    parsed.protocol !== "https:" ||
    !parsed.hostname ||
    parsed.username ||
    parsed.password ||
    parsed.pathname !== "/" ||
    parsed.search ||
    parsed.hash
  ) {
    throw new Error("canonical origin must be a credential-free HTTPS origin without a path, query, or fragment.");
  }
  return parsed.origin;
}

export function parseAndNormalizeTenantConfig(rawText) {
  const raw = parseJsonWithDuplicateKeys(rawText);
  assertExactObject(raw, ROOT_FIELDS, "tenant");
  assertNoSecretValues(raw, "tenant");

  const tenantId = requiredString(raw["tenant/id"], "tenant/id");
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/i.test(tenantId)) {
    fieldError("tenant/id", "must use letters, numbers, and hyphens only.");
  }
  const brandName = requiredString(raw["brand/name"], "brand/name");
  const brandLogoUrl = optionalPublicUrl(raw["brand/logo-url"], "brand/logo-url");
  const requestedTheme = requiredString(raw["theme/id"], "theme/id").toLowerCase();
  const themeId = requestedTheme === "default" ? "dark" : requestedTheme;
  if (!THEMES.has(themeId)) {
    fieldError("theme/id", "is unsupported.");
  }

  const rawFeatures = assertExactObject(raw.features, FEATURE_FIELDS, "features");
  const features = {
    terminal: requiredBoolean(rawFeatures.terminal, "features.terminal"),
    analytics: requiredBoolean(rawFeatures.analytics, "features.analytics"),
    affiliate: requiredBoolean(rawFeatures.affiliate, "features.affiliate"),
  };
  if (!features.terminal && !features.analytics) {
    fieldError("features", "must enable terminal or analytics.");
  }

  const rawVenue = assertExactObject(raw.venue, VENUE_FIELDS, "venue");
  const venueId = requiredString(rawVenue.id, "venue.id").toLowerCase();
  if (venueId !== "hyperliquid") {
    fieldError("venue.id", "is unsupported.");
  }
  const venue = {
    id: venueId,
    label: requiredString(rawVenue.label, "venue.label"),
    url: optionalPublicUrl(rawVenue.url, "venue.url"),
  };

  const rawAffiliate = assertExactObject(raw.affiliate, AFFILIATE_FIELDS, "affiliate");
  const status = requiredString(rawAffiliate.status, "affiliate.status").toLowerCase();
  if (!AFFILIATE_STATUSES.has(status)) {
    fieldError("affiliate.status", "is unsupported.");
  }
  const affiliate = {
    provider: rawAffiliate.provider,
    id: rawAffiliate.id,
    status,
    "referral-url": optionalPublicUrl(rawAffiliate["referral-url"], "affiliate.referral-url"),
    "event-endpoint": optionalPublicUrl(rawAffiliate["event-endpoint"], "affiliate.event-endpoint"),
    disclosure: requiredString(rawAffiliate.disclosure, "affiliate.disclosure"),
  };
  if (status === "configured" || status === "enabled") {
    if (affiliate.provider !== "hyperliquid") {
      fieldError("affiliate.provider", "must be hyperliquid when configured.");
    }
    affiliate.id = requiredString(affiliate.id, "affiliate.id");
  } else if (affiliate.provider !== null || affiliate.id !== null || affiliate["referral-url"] !== "") {
    fieldError("affiliate", "must not include provider, id, or referral URL when unavailable.");
  }

  return {
    "tenant/id": tenantId,
    "brand/name": brandName,
    "brand/logo-url": brandLogoUrl,
    "theme/id": themeId,
    features,
    venue,
    affiliate,
  };
}

function canonicalize(value) {
  if (Array.isArray(value)) {
    return value.map(canonicalize);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, canonicalize(value[key])])
    );
  }
  return value;
}

export function canonicalTenantJson(normalizedTenant) {
  return JSON.stringify(canonicalize(normalizedTenant));
}

export function tenantConfigDigest(normalizedTenant) {
  return crypto
    .createHash("sha256")
    .update(canonicalTenantJson(normalizedTenant))
    .digest("hex")
    .toUpperCase();
}

export function enabledTenantRoutes(normalizedTenant) {
  const features = normalizedTenant?.features || {};
  return [
    ...(features.terminal === true ? ["/trade"] : []),
    ...(features.analytics === true ? ["/portfolio"] : []),
  ];
}
