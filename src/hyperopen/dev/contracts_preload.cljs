(ns hyperopen.dev.contracts-preload
  "Dev-only preload that loads the cljs.spec contract tree.

   Loading hyperopen.schema.contracts registers every action/effect/state spec
   and installs the assertion + effect-order functions into
   hyperopen.runtime.validation's indirections (see install-contracts-impl! and
   install-effect-order-contract-impl!). Release builds do not load this preload,
   carry no other require edge into the contract tree, and therefore
   dead-code-eliminate the entire ~280KB spec registry."
  (:require [hyperopen.portfolio.optimizer.contracts.spec-registry]
            [hyperopen.schema.contracts]))
