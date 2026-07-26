// Minimal MsgPack encoder for Hyperliquid action signing.
// Supports: null, booleans, numbers, strings, arrays, objects, Uint8Array.
// ASCII-only file.

const textEncoder = new TextEncoder();

function pushU8(out, v) { out.push(v & 0xff); }
function pushU16(out, v) { out.push((v >> 8) & 0xff, v & 0xff); }
function pushU32(out, v) {
  out.push((v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff);
}
function pushU64(out, v) {
  const value = BigInt(v);
  for (let shift = 56n; shift >= 0n; shift -= 8n) {
    out.push(Number((value >> shift) & 0xffn));
  }
}
function pushI16(out, v) { pushU16(out, v & 0xffff); }
function pushI32(out, v) { pushU32(out, v >>> 0); }
function pushI64(out, v) {
  pushU64(out, BigInt.asUintN(64, BigInt(v)));
}
function pushF64(out, v) {
  const buf = new ArrayBuffer(8);
  const view = new DataView(buf);
  view.setFloat64(0, v, false);
  for (let i = 0; i < 8; i++) out.push(view.getUint8(i));
}

function encodeValue(out, value) {
  if (value === null || value === undefined) {
    pushU8(out, 0xc0);
    return;
  }
  if (value === true) { pushU8(out, 0xc3); return; }
  if (value === false) { pushU8(out, 0xc2); return; }

  if (value instanceof Uint8Array) {
    const len = value.length;
    if (len < 256) { pushU8(out, 0xc4); pushU8(out, len); }
    else if (len < 65536) { pushU8(out, 0xc5); pushU16(out, len); }
    else { pushU8(out, 0xc6); pushU32(out, len); }
    for (let i = 0; i < len; i++) out.push(value[i]);
    return;
  }

  if (Array.isArray(value)) {
    const len = value.length;
    if (len < 16) { pushU8(out, 0x90 | len); }
    else if (len < 65536) { pushU8(out, 0xdc); pushU16(out, len); }
    else { pushU8(out, 0xdd); pushU32(out, len); }
    for (const v of value) encodeValue(out, v);
    return;
  }

  if (typeof value === "number") {
    if (Number.isInteger(value)) {
      if (value >= 0 && value <= 0x7f) { pushU8(out, value); return; }
      if (value < 0 && value >= -32) { pushU8(out, 0xe0 | (value + 32)); return; }
      if (value >= 0 && value <= 0xff) { pushU8(out, 0xcc); pushU8(out, value); return; }
      if (value >= 0 && value <= 0xffff) { pushU8(out, 0xcd); pushU16(out, value); return; }
      if (value >= 0 && value <= 0xffffffff) { pushU8(out, 0xce); pushU32(out, value); return; }
      if (value >= -128 && value <= 127) { pushU8(out, 0xd0); pushU8(out, value & 0xff); return; }
      if (value >= -32768 && value <= 32767) { pushU8(out, 0xd1); pushI16(out, value); return; }
      if (value >= -2147483648 && value <= 2147483647) { pushU8(out, 0xd2); pushI32(out, value); return; }
      if (Number.isSafeInteger(value) && value >= 0) { pushU8(out, 0xcf); pushU64(out, value); return; }
      if (Number.isSafeInteger(value) && value < 0) { pushU8(out, 0xd3); pushI64(out, value); return; }
    }
    pushU8(out, 0xcb); pushF64(out, value); return;
  }

  if (typeof value === "string") {
    const bytes = textEncoder.encode(value);
    const len = bytes.length;
    if (len < 32) { pushU8(out, 0xa0 | len); }
    else if (len < 256) { pushU8(out, 0xd9); pushU8(out, len); }
    else if (len < 65536) { pushU8(out, 0xda); pushU16(out, len); }
    else { pushU8(out, 0xdb); pushU32(out, len); }
    for (let i = 0; i < len; i++) out.push(bytes[i]);
    return;
  }

  if (typeof value === "object") {
    const entries = Object.entries(value);
    const len = entries.length;
    if (len < 16) { pushU8(out, 0x80 | len); }
    else if (len < 65536) { pushU8(out, 0xde); pushU16(out, len); }
    else { pushU8(out, 0xdf); pushU32(out, len); }
    for (const [k, v] of entries) {
      encodeValue(out, k);
      encodeValue(out, v);
    }
    return;
  }

  // Fallback
  pushU8(out, 0xc0);
}

export function encode(value) {
  const out = [];
  encodeValue(out, value);
  return new Uint8Array(out);
}
