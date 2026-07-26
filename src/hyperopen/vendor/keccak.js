// Keccak-256 implementation using BigInt (no external deps).
// Exposes: keccak256(Uint8Array|string) -> hex string (lowercase, no 0x).
// ASCII-only file.

const RC = [
  0x0000000000000001n, 0x0000000000008082n,
  0x800000000000808an, 0x8000000080008000n,
  0x000000000000808bn, 0x0000000080000001n,
  0x8000000080008081n, 0x8000000000008009n,
  0x000000000000008an, 0x0000000000000088n,
  0x0000000080008009n, 0x000000008000000an,
  0x000000008000808bn, 0x800000000000008bn,
  0x8000000000008089n, 0x8000000000008003n,
  0x8000000000008002n, 0x8000000000000080n,
  0x000000000000800an, 0x800000008000000an,
  0x8000000080008081n, 0x8000000000008080n,
  0x0000000080000001n, 0x8000000080008008n
];

const ROT = [
  [0, 36, 3, 41, 18],
  [1, 44, 10, 45, 2],
  [62, 6, 43, 15, 61],
  [28, 55, 25, 21, 56],
  [27, 20, 39, 8, 14]
];

function rotl(x, n) {
  n = BigInt(n);
  return ((x << n) | (x >> (64n - n))) & 0xffffffffffffffffn;
}

function keccakF(state) {
  for (let round = 0; round < 24; round++) {
    // Theta
    const C = new Array(5).fill(0n);
    for (let x = 0; x < 5; x++) {
      C[x] = state[x] ^ state[x + 5] ^ state[x + 10] ^ state[x + 15] ^ state[x + 20];
    }
    const D = new Array(5).fill(0n);
    for (let x = 0; x < 5; x++) {
      D[x] = C[(x + 4) % 5] ^ rotl(C[(x + 1) % 5], 1);
    }
    for (let x = 0; x < 5; x++) {
      for (let y = 0; y < 5; y++) {
        state[x + 5 * y] ^= D[x];
      }
    }

    // Rho and Pi
    const B = new Array(25).fill(0n);
    for (let x = 0; x < 5; x++) {
      for (let y = 0; y < 5; y++) {
        const rot = ROT[x][y];
        const nx = y;
        const ny = (2 * x + 3 * y) % 5;
        B[nx + 5 * ny] = rotl(state[x + 5 * y], rot);
      }
    }

    // Chi
    for (let x = 0; x < 5; x++) {
      for (let y = 0; y < 5; y++) {
        state[x + 5 * y] = B[x + 5 * y] ^ ((~B[((x + 1) % 5) + 5 * y]) & B[((x + 2) % 5) + 5 * y]);
      }
    }

    // Iota
    state[0] ^= RC[round];
  }
}

function toBytes(data) {
  if (data instanceof Uint8Array) return data;
  if (typeof data === 'string') return new TextEncoder().encode(data);
  return new Uint8Array(0);
}

export function keccak256(data) {
  const input = toBytes(data);
  const rate = 136; // 1088 bits
  const state = new Array(25).fill(0n);

  let offset = 0;
  while (offset + rate <= input.length) {
    for (let i = 0; i < rate; i++) {
      const lane = (i / 8) | 0;
      const shift = BigInt((i % 8) * 8);
      state[lane] ^= BigInt(input[offset + i]) << shift;
    }
    keccakF(state);
    offset += rate;
  }

  // Pad
  const block = new Uint8Array(rate);
  const remaining = input.length - offset;
  if (remaining > 0) block.set(input.subarray(offset));
  block[remaining] |= 0x01;
  block[rate - 1] |= 0x80;
  for (let i = 0; i < rate; i++) {
    const lane = (i / 8) | 0;
    const shift = BigInt((i % 8) * 8);
    state[lane] ^= BigInt(block[i]) << shift;
  }
  keccakF(state);

  // Squeeze 32 bytes
  const out = new Uint8Array(32);
  for (let i = 0; i < 32; i++) {
    const lane = (i / 8) | 0;
    const shift = BigInt((i % 8) * 8);
    out[i] = Number((state[lane] >> shift) & 0xffn);
  }

  let hex = '';
  for (let i = 0; i < out.length; i++) {
    const b = out[i];
    hex += (b < 16 ? '0' : '') + b.toString(16);
  }
  return hex;
}
