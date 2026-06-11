(ns hyperopen.dev.contracts-preload
  "Dev-only preload that loads the cljs.spec contract tree.

   Loading hyperopen.schema.contracts registers every action/effect/state spec
   and installs the assertion functions into hyperopen.runtime.validation's
   indirection (see install-contracts-impl!). Release builds do not load this
   preload, carry no other require edge into the contract tree, and therefore
   dead-code-eliminate the entire ~280KB spec registry."
  (:require [hyperopen.schema.contracts]))
