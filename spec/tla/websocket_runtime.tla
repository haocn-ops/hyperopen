---- MODULE websocket_runtime ----
EXTENDS FiniteSets, Naturals, Sequences

CONSTANTS MaxQueueSize,
          MaxSocketId,
          MaxCounter,
          MaxSeq,
          MaxSteps,
          SubOrder1,
          SubOrder2,
          SubOrder3,
          RawMessageA,
          RawMessageB,
          CoalesceKeyA,
          CoalesceKeyB

None == "none"
NoSocket == 0
NoSeq == 0

Statuses == {"disconnected", "connecting", "connected", "reconnecting"}
TimerKeys == {"retry", "health-tick", "watchdog", "market-flush"}
StaleActions == {"stale_socket_open", "stale_socket_close", "stale_decoded", "stale_parse_error"}
SubOrder == <<SubOrder1, SubOrder2, SubOrder3>>
RawMessageIds == {RawMessageA, RawMessageB}
CoalesceKeys == {CoalesceKeyA, CoalesceKeyB}
SUBS == {SubOrder[i] : i \in 1..Len(SubOrder)}
MarketSubs == {SubOrder1, SubOrder3}
LosslessSubs == SUBS \ MarketSubs
SeqDomain == 1..MaxSeq

MarketKeyForSub(sub) ==
  CASE sub = SubOrder1 -> CoalesceKeyA
    [] sub = SubOrder3 -> CoalesceKeyB

SubscribeMsg(sub) == [kind |-> "subscribe", sub |-> sub, id |-> None]
UnsubscribeMsg(sub) == [kind |-> "unsubscribe", sub |-> sub, id |-> None]
RawMsg(rawId) == [kind |-> "raw", sub |-> None, id |-> rawId]

IsOutbound(msg) ==
  /\ msg.kind \in {"subscribe", "unsubscribe", "raw"}
  /\ IF msg.sub = None THEN TRUE ELSE msg.sub \in SUBS
  /\ IF msg.id = None THEN TRUE ELSE msg.id \in RawMessageIds
  /\ (msg.kind = "raw") => /\ msg.id \in RawMessageIds /\ msg.sub = None
  /\ (msg.kind # "raw") => /\ msg.sub \in SUBS /\ msg.id = None

EmptyGap == [expected |-> 0, actual |-> 0]
EmptyPending == [k \in CoalesceKeys |-> NoSeq]
EmptyMetrics ==
  [marketCoalesced |-> 0,
   marketDispatched |-> 0,
   losslessDispatched |-> 0,
   ingressParseErrors |-> 0]

EmptyStream ==
  [subscribed |-> FALSE,
   lastSeq |-> NoSeq,
   seqGapDetected |-> FALSE,
   seqGapCount |-> 0,
   lastGap |-> EmptyGap]

EmptyStreams == [s \in SUBS |-> EmptyStream]

InitRuntime ==
  [wsConfigured |-> FALSE,
   status |-> "disconnected",
   socketId |-> 0,
   activeSocketId |-> NoSocket,
   online |-> TRUE,
   hidden |-> FALSE,
   intentionalClose |-> FALSE,
   retryTimerActive |-> FALSE,
   healthTickActive |-> FALSE,
   watchdogActive |-> FALSE,
   marketFlushActive |-> FALSE,
   queue |-> <<>>,
   desiredSubscriptions |-> {},
   streams |-> EmptyStreams,
   marketPending |-> EmptyPending,
   metrics |-> EmptyMetrics,
   nextRetryAt |-> None,
   attempt |-> 0]

VARIABLES runtime,
          prevRuntime,
          stepCount,
          lastAction,
          lastTimerSet,
          lastTimerCleared,
          lastSendBatch,
          lastTouchedSub,
          lastTouchedSeq,
          lastTeardownSocketId

vars ==
  <<runtime,
    prevRuntime,
    stepCount,
    lastAction,
    lastTimerSet,
    lastTimerCleared,
    lastSendBatch,
    lastTouchedSub,
    lastTouchedSeq,
    lastTeardownSocketId>>

Bump(n) == IF n < MaxCounter THEN n + 1 ELSE MaxCounter

BumpBy(n, delta) ==
  IF n + delta <= MaxCounter THEN n + delta ELSE MaxCounter

BoundedAppend(queue, msg) ==
  LET nextQueue == Append(queue, msg)
  IN IF Len(nextQueue) > MaxQueueSize THEN Tail(nextQueue) ELSE nextQueue

HasPending(pending) ==
  \E key \in CoalesceKeys : pending[key] # NoSeq

PendingCount(pending) ==
  Cardinality({key \in CoalesceKeys : pending[key] # NoSeq})

HasStaleSocket(rt) ==
  /\ rt.socketId > 0
  /\ (rt.activeSocketId = NoSocket \/ rt.activeSocketId < rt.socketId)

CanAdvance ==
  stepCount < MaxSteps

CanConnect(rt) ==
  /\ rt.wsConfigured
  /\ rt.online
  /\ ~rt.intentionalClose
  /\ rt.activeSocketId = NoSocket
  /\ rt.socketId < MaxSocketId

EnsureConnect(rt) ==
  IF CanConnect(rt)
    THEN [rt EXCEPT
            !.socketId = rt.socketId + 1,
            !.activeSocketId = rt.socketId + 1,
            !.status = IF rt.attempt > 0 THEN "reconnecting" ELSE "connecting",
            !.nextRetryAt = None]
    ELSE rt

ResetStream(subscribed) ==
  [subscribed |-> subscribed,
   lastSeq |-> NoSeq,
   seqGapDetected |-> FALSE,
   seqGapCount |-> 0,
   lastGap |-> EmptyGap]

ReplayStreamsAsActive(streams, desired) ==
  [s \in SUBS |->
     IF s \in desired
       THEN ResetStream(TRUE)
       ELSE streams[s]]

UpdateStreamForSeq(stream, seq) ==
  IF ~stream.subscribed
    THEN stream
    ELSE LET priorSeq == stream.lastSeq
             gap == priorSeq # NoSeq /\ seq > priorSeq + 1
             nextLastSeq ==
               IF priorSeq = NoSeq
                 THEN seq
                 ELSE IF seq > priorSeq THEN seq ELSE priorSeq
             nextGap ==
               IF gap
                 THEN [expected |-> priorSeq + 1, actual |-> seq]
                 ELSE stream.lastGap
         IN [subscribed |-> stream.subscribed,
             lastSeq |-> nextLastSeq,
             seqGapDetected |-> IF gap THEN TRUE ELSE stream.seqGapDetected,
             seqGapCount |-> IF gap THEN Bump(stream.seqGapCount) ELSE stream.seqGapCount,
             lastGap |-> nextGap]

DesiredReplay(desired) ==
  SelectSeq([i \in 1..Len(SubOrder) |-> SubscribeMsg(SubOrder[i])],
            LAMBDA msg : msg.sub \in desired)

TypeGap(gap) ==
  /\ gap.expected \in 0..(MaxSeq + 1)
  /\ gap.actual \in 0..MaxSeq

TypeStream(stream) ==
  /\ stream.subscribed \in BOOLEAN
  /\ stream.lastSeq \in 0..MaxSeq
  /\ stream.seqGapDetected \in BOOLEAN
  /\ stream.seqGapCount \in 0..MaxCounter
  /\ TypeGap(stream.lastGap)

TypeMetrics(metrics) ==
  /\ metrics.marketCoalesced \in 0..MaxCounter
  /\ metrics.marketDispatched \in 0..MaxCounter
  /\ metrics.losslessDispatched \in 0..MaxCounter
  /\ metrics.ingressParseErrors \in 0..MaxCounter

TypePending(value) ==
  value \in 0..MaxSeq

TypeRuntime(rt) ==
  /\ rt.wsConfigured \in BOOLEAN
  /\ rt.status \in Statuses
  /\ rt.socketId \in Nat
  /\ rt.activeSocketId \in 0..MaxSocketId
  /\ rt.online \in BOOLEAN
  /\ rt.hidden \in BOOLEAN
  /\ rt.intentionalClose \in BOOLEAN
  /\ rt.retryTimerActive \in BOOLEAN
  /\ rt.healthTickActive \in BOOLEAN
  /\ rt.watchdogActive \in BOOLEAN
  /\ rt.marketFlushActive \in BOOLEAN
  /\ rt.desiredSubscriptions \subseteq SUBS
  /\ Len(rt.queue) <= MaxQueueSize
  /\ \A i \in 1..Len(rt.queue) : IsOutbound(rt.queue[i])
  /\ DOMAIN rt.streams = SUBS
  /\ \A sub \in SUBS : TypeStream(rt.streams[sub])
  /\ DOMAIN rt.marketPending = CoalesceKeys
  /\ \A key \in CoalesceKeys : TypePending(rt.marketPending[key])
  /\ TypeMetrics(rt.metrics)
  /\ IF rt.nextRetryAt = None THEN TRUE ELSE rt.nextRetryAt = "scheduled"
  /\ rt.attempt \in 0..MaxCounter

Init ==
  /\ runtime = InitRuntime
  /\ prevRuntime = InitRuntime
  /\ stepCount = 0
  /\ lastAction = "init"
  /\ lastTimerSet = {}
  /\ lastTimerCleared = {}
  /\ lastSendBatch = <<>>
  /\ lastTouchedSub = None
  /\ lastTouchedSeq = NoSeq
  /\ lastTeardownSocketId = NoSocket

InitConnection ==
  LET startWatchdog == ~runtime.watchdogActive
      startHealth == ~runtime.healthTickActive
      base1 == [runtime EXCEPT
                  !.wsConfigured = TRUE,
                  !.intentionalClose = FALSE]
      base2 == IF startWatchdog THEN [base1 EXCEPT !.watchdogActive = TRUE] ELSE base1
      base3 == IF startHealth THEN [base2 EXCEPT !.healthTickActive = TRUE] ELSE base2
      nextRuntime == EnsureConnect(base3)
      timerSet ==
        (IF startWatchdog THEN {"watchdog"} ELSE {})
          \cup
        (IF startHealth THEN {"health-tick"} ELSE {})
  IN /\ CanAdvance
     /\ ~runtime.wsConfigured
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "init_connection"
     /\ lastTimerSet' = timerSet
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

Disconnect ==
  LET cleared ==
        (IF runtime.retryTimerActive THEN {"retry"} ELSE {})
          \cup
        (IF runtime.watchdogActive THEN {"watchdog"} ELSE {})
          \cup
        (IF runtime.healthTickActive THEN {"health-tick"} ELSE {})
          \cup
        (IF runtime.marketFlushActive THEN {"market-flush"} ELSE {})
      nextRuntime ==
        [runtime EXCEPT
           !.retryTimerActive = FALSE,
           !.watchdogActive = FALSE,
           !.healthTickActive = FALSE,
           !.marketFlushActive = FALSE,
           !.marketPending = EmptyPending,
           !.intentionalClose = TRUE,
           !.status = "disconnected",
           !.activeSocketId = NoSocket,
           !.nextRetryAt = None]
  IN /\ CanAdvance
     /\ (runtime.wsConfigured
         \/ runtime.activeSocketId # NoSocket
         \/ runtime.status # "disconnected"
         \/ runtime.retryTimerActive
         \/ runtime.watchdogActive
         \/ runtime.healthTickActive
         \/ runtime.marketFlushActive
         \/ HasPending(runtime.marketPending))
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "disconnect"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = cleared
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = runtime.activeSocketId

ForceReconnect ==
  LET startHealth == ~runtime.healthTickActive
      cleared ==
        (IF runtime.retryTimerActive THEN {"retry"} ELSE {})
          \cup
        (IF runtime.marketFlushActive THEN {"market-flush"} ELSE {})
      base1 ==
        [runtime EXCEPT
           !.retryTimerActive = FALSE,
           !.marketFlushActive = FALSE,
           !.marketPending = EmptyPending,
           !.intentionalClose = FALSE,
           !.activeSocketId = NoSocket,
           !.nextRetryAt = None]
      base2 == IF startHealth THEN [base1 EXCEPT !.healthTickActive = TRUE] ELSE base1
      nextRuntime == EnsureConnect(base2)
      timerSet == IF startHealth THEN {"health-tick"} ELSE {}
  IN /\ CanAdvance
     /\ runtime.wsConfigured
     /\ (runtime.activeSocketId # NoSocket
         \/ runtime.status \in {"connected", "connecting", "reconnecting"}
         \/ runtime.retryTimerActive
         \/ runtime.marketFlushActive
         \/ HasPending(runtime.marketPending))
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "force_reconnect"
     /\ lastTimerSet' = timerSet
     /\ lastTimerCleared' = cleared
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = runtime.activeSocketId

SendSubscribe(sub) ==
  LET data == SubscribeMsg(sub)
      immediate == runtime.status = "connected" /\ runtime.activeSocketId # NoSocket
      nextDesired == runtime.desiredSubscriptions \cup {sub}
      nextStreams == [runtime.streams EXCEPT ![sub] = ResetStream(TRUE)]
      base1 == [runtime EXCEPT
                  !.desiredSubscriptions = nextDesired,
                  !.streams = nextStreams]
      base2 == IF immediate THEN base1 ELSE [base1 EXCEPT !.queue = BoundedAppend(base1.queue, data)]
      nextRuntime == IF immediate THEN base2 ELSE EnsureConnect(base2)
  IN /\ CanAdvance
     /\ runtime.wsConfigured
     /\ (sub \notin runtime.desiredSubscriptions
         \/ runtime.streams[sub].seqGapDetected)
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "send_subscribe"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = IF immediate THEN <<data>> ELSE <<>>
     /\ lastTouchedSub' = sub
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

SendUnsubscribe(sub) ==
  LET data == UnsubscribeMsg(sub)
      immediate == runtime.status = "connected" /\ runtime.activeSocketId # NoSocket
      nextDesired == runtime.desiredSubscriptions \ {sub}
      nextStreams == [runtime.streams EXCEPT ![sub] = ResetStream(FALSE)]
      base1 == [runtime EXCEPT
                  !.desiredSubscriptions = nextDesired,
                  !.streams = nextStreams]
      base2 == IF immediate THEN base1 ELSE [base1 EXCEPT !.queue = BoundedAppend(base1.queue, data)]
      nextRuntime == IF immediate THEN base2 ELSE EnsureConnect(base2)
  IN /\ CanAdvance
     /\ runtime.wsConfigured
     /\ sub \in runtime.desiredSubscriptions
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "send_unsubscribe"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = IF immediate THEN <<data>> ELSE <<>>
     /\ lastTouchedSub' = sub
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

SendRaw(rawId) ==
  LET data == RawMsg(rawId)
      immediate == runtime.status = "connected" /\ runtime.activeSocketId # NoSocket
      base1 == IF immediate THEN runtime ELSE [runtime EXCEPT !.queue = BoundedAppend(runtime.queue, data)]
      nextRuntime == IF immediate THEN base1 ELSE EnsureConnect(base1)
  IN /\ CanAdvance
     /\ runtime.wsConfigured
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "send_raw"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = IF immediate THEN <<data>> ELSE <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

ActiveSocketOpen ==
  LET clearedRetry == IF runtime.retryTimerActive THEN {"retry"} ELSE {}
      replay == DesiredReplay(runtime.desiredSubscriptions) \o runtime.queue
      nextRuntime ==
        [runtime EXCEPT
           !.retryTimerActive = FALSE,
           !.nextRetryAt = None,
           !.streams = ReplayStreamsAsActive(runtime.streams, runtime.desiredSubscriptions),
           !.status = "connected",
           !.attempt = 0,
           !.queue = <<>>]
  IN /\ CanAdvance
     /\ runtime.activeSocketId # NoSocket
     /\ runtime.status \in {"connecting", "reconnecting"}
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "socket_open_active"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = clearedRetry
     /\ lastSendBatch' = replay
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

StaleSocketOpen ==
  /\ CanAdvance
  /\ HasStaleSocket(runtime)
  /\ runtime' = runtime
  /\ prevRuntime' = runtime
  /\ stepCount' = stepCount + 1
  /\ lastAction' = "stale_socket_open"
  /\ lastTimerSet' = {}
  /\ lastTimerCleared' = {}
  /\ lastSendBatch' = <<>>
  /\ lastTouchedSub' = None
  /\ lastTouchedSeq' = NoSeq
  /\ lastTeardownSocketId' = NoSocket

ActiveSocketClose ==
  LET cleared ==
        (IF runtime.retryTimerActive THEN {"retry"} ELSE {})
          \cup
        (IF runtime.marketFlushActive THEN {"market-flush"} ELSE {})
      base1 ==
        [runtime EXCEPT
           !.activeSocketId = NoSocket,
           !.marketFlushActive = FALSE,
           !.marketPending = EmptyPending]
      nextRuntime ==
        IF runtime.intentionalClose \/ ~runtime.wsConfigured \/ ~runtime.online
          THEN [base1 EXCEPT
                  !.status = "disconnected",
                  !.retryTimerActive = FALSE,
                  !.nextRetryAt = None]
          ELSE [base1 EXCEPT
                  !.attempt = Bump(base1.attempt),
                  !.status = "reconnecting",
                  !.retryTimerActive = TRUE,
                  !.nextRetryAt = "scheduled"]
      timerSet ==
        IF runtime.intentionalClose \/ ~runtime.wsConfigured \/ ~runtime.online
          THEN {}
          ELSE {"retry"}
  IN /\ CanAdvance
     /\ runtime.activeSocketId # NoSocket
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "socket_close_active"
     /\ lastTimerSet' = timerSet
     /\ lastTimerCleared' = cleared
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = runtime.activeSocketId

StaleSocketClose ==
  /\ CanAdvance
  /\ HasStaleSocket(runtime)
  /\ runtime' = runtime
  /\ prevRuntime' = runtime
  /\ stepCount' = stepCount + 1
  /\ lastAction' = "stale_socket_close"
  /\ lastTimerSet' = {}
  /\ lastTimerCleared' = {}
  /\ lastSendBatch' = <<>>
  /\ lastTouchedSub' = None
  /\ lastTouchedSeq' = NoSeq
  /\ lastTeardownSocketId' = NoSocket

LifecycleOnline ==
  LET nextRuntime == EnsureConnect([runtime EXCEPT !.online = TRUE])
  IN /\ CanAdvance
     /\ ~runtime.online
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "lifecycle_online"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

LifecycleOffline ==
  LET cleared ==
        (IF runtime.retryTimerActive THEN {"retry"} ELSE {})
          \cup
        (IF runtime.marketFlushActive THEN {"market-flush"} ELSE {})
      nextRuntime ==
        [runtime EXCEPT
           !.online = FALSE,
           !.status = "disconnected",
           !.activeSocketId = NoSocket,
           !.retryTimerActive = FALSE,
           !.nextRetryAt = None,
           !.marketFlushActive = FALSE,
           !.marketPending = EmptyPending]
  IN /\ CanAdvance
     /\ runtime.online
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "lifecycle_offline"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = cleared
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = runtime.activeSocketId

LifecycleVisible ==
  LET nextRuntime == EnsureConnect([runtime EXCEPT !.hidden = FALSE])
  IN /\ CanAdvance
     /\ runtime.hidden
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "lifecycle_visible"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

LifecycleHidden ==
  /\ CanAdvance
  /\ ~runtime.hidden
  /\ runtime' = [runtime EXCEPT !.hidden = TRUE]
  /\ prevRuntime' = runtime
  /\ stepCount' = stepCount + 1
  /\ lastAction' = "lifecycle_hidden"
  /\ lastTimerSet' = {}
  /\ lastTimerCleared' = {}
  /\ lastSendBatch' = <<>>
  /\ lastTouchedSub' = None
  /\ lastTouchedSeq' = NoSeq
  /\ lastTeardownSocketId' = NoSocket

RetryTimerFired ==
  LET base1 == [runtime EXCEPT
                  !.retryTimerActive = FALSE,
                  !.nextRetryAt = None]
      nextRuntime == EnsureConnect(base1)
  IN /\ CanAdvance
     /\ runtime.retryTimerActive
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "retry_timer_fired"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {"retry"}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

ActiveDecodedMarket(sub, key, seq) ==
  LET replacing == runtime.marketPending[key] # NoSeq
      nextStreams == [runtime.streams EXCEPT ![sub] = UpdateStreamForSeq(runtime.streams[sub], seq)]
      nextPending == [runtime.marketPending EXCEPT ![key] = seq]
      nextMetrics ==
        [runtime.metrics EXCEPT
           !.marketCoalesced = IF replacing THEN Bump(runtime.metrics.marketCoalesced) ELSE runtime.metrics.marketCoalesced]
      nextRuntime ==
        [runtime EXCEPT
           !.streams = nextStreams,
           !.marketPending = nextPending,
           !.marketFlushActive = TRUE,
           !.metrics = nextMetrics]
      timerSet == IF runtime.marketFlushActive THEN {} ELSE {"market-flush"}
  IN /\ CanAdvance
     /\ runtime.activeSocketId # NoSocket
     /\ runtime.status = "connected"
     /\ sub \in MarketSubs
     /\ key = MarketKeyForSub(sub)
     /\ runtime.streams[sub].subscribed
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "decoded_market"
     /\ lastTimerSet' = timerSet
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = sub
     /\ lastTouchedSeq' = seq
     /\ lastTeardownSocketId' = NoSocket

ActiveDecodedLossless(sub, seq) ==
  LET nextStreams == [runtime.streams EXCEPT ![sub] = UpdateStreamForSeq(runtime.streams[sub], seq)]
      nextMetrics ==
        [runtime.metrics EXCEPT
           !.losslessDispatched = Bump(runtime.metrics.losslessDispatched)]
      nextRuntime ==
        [runtime EXCEPT
           !.streams = nextStreams,
           !.metrics = nextMetrics]
  IN /\ CanAdvance
     /\ runtime.activeSocketId # NoSocket
     /\ runtime.status = "connected"
     /\ sub \in LosslessSubs
     /\ runtime.streams[sub].subscribed
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "decoded_lossless"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = sub
     /\ lastTouchedSeq' = seq
     /\ lastTeardownSocketId' = NoSocket

StaleDecoded ==
  /\ CanAdvance
  /\ HasStaleSocket(runtime)
  /\ runtime' = runtime
  /\ prevRuntime' = runtime
  /\ stepCount' = stepCount + 1
  /\ lastAction' = "stale_decoded"
  /\ lastTimerSet' = {}
  /\ lastTimerCleared' = {}
  /\ lastSendBatch' = <<>>
  /\ lastTouchedSub' = None
  /\ lastTouchedSeq' = NoSeq
  /\ lastTeardownSocketId' = NoSocket

MarketFlushTimerFired ==
  LET dispatched == PendingCount(runtime.marketPending)
      nextMetrics ==
        [runtime.metrics EXCEPT
           !.marketDispatched = BumpBy(runtime.metrics.marketDispatched, dispatched)]
      nextRuntime ==
        [runtime EXCEPT
           !.marketPending = EmptyPending,
           !.marketFlushActive = FALSE,
           !.metrics = nextMetrics]
  IN /\ CanAdvance
     /\ runtime.marketFlushActive
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "timer_market_flush"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

ActiveParseError ==
  LET nextMetrics ==
        [runtime.metrics EXCEPT
           !.ingressParseErrors = Bump(runtime.metrics.ingressParseErrors)]
      nextRuntime == [runtime EXCEPT !.metrics = nextMetrics]
  IN /\ CanAdvance
     /\ runtime.activeSocketId # NoSocket
     /\ runtime.status = "connected"
     /\ runtime' = nextRuntime
     /\ prevRuntime' = runtime
     /\ stepCount' = stepCount + 1
     /\ lastAction' = "parse_error_active"
     /\ lastTimerSet' = {}
     /\ lastTimerCleared' = {}
     /\ lastSendBatch' = <<>>
     /\ lastTouchedSub' = None
     /\ lastTouchedSeq' = NoSeq
     /\ lastTeardownSocketId' = NoSocket

StaleParseError ==
  /\ CanAdvance
  /\ HasStaleSocket(runtime)
  /\ runtime' = runtime
  /\ prevRuntime' = runtime
  /\ stepCount' = stepCount + 1
  /\ lastAction' = "stale_parse_error"
  /\ lastTimerSet' = {}
  /\ lastTimerCleared' = {}
  /\ lastSendBatch' = <<>>
  /\ lastTouchedSub' = None
  /\ lastTouchedSeq' = NoSeq
  /\ lastTeardownSocketId' = NoSocket

CoreNext ==
  \/ InitConnection
  \/ \E sub \in SUBS : SendSubscribe(sub)
  \/ \E sub \in SUBS : SendUnsubscribe(sub)
  \/ \E rawId \in RawMessageIds : SendRaw(rawId)
  \/ ActiveSocketOpen
  \/ ActiveSocketClose
  \/ StaleSocketOpen
  \/ StaleSocketClose
  \/ LifecycleOnline
  \/ LifecycleOffline
  \/ LifecycleVisible
  \/ LifecycleHidden
  \/ RetryTimerFired
  \/ \E sub \in SUBS, key \in CoalesceKeys, seq \in SeqDomain : ActiveDecodedMarket(sub, key, seq)
  \/ \E sub \in SUBS, seq \in SeqDomain : ActiveDecodedLossless(sub, seq)
  \/ StaleDecoded
  \/ MarketFlushTimerFired
  \/ ActiveParseError
  \/ StaleParseError
  \/ Disconnect
  \/ ForceReconnect

Next ==
  CoreNext

Spec == Init /\ [][Next]_vars

ConnectLivenessRuntime ==
  [InitRuntime EXCEPT
     !.wsConfigured = TRUE,
     !.status = "reconnecting",
     !.retryTimerActive = TRUE,
     !.nextRetryAt = "scheduled",
     !.attempt = 1]

FlushLivenessRuntime ==
  [InitRuntime EXCEPT
     !.wsConfigured = TRUE,
     !.status = "connected",
     !.socketId = 1,
     !.activeSocketId = 1,
     !.marketFlushActive = TRUE,
     !.marketPending[CoalesceKeyA] = 1]

ConnectLivenessInit ==
  /\ runtime = ConnectLivenessRuntime
  /\ prevRuntime = ConnectLivenessRuntime
  /\ stepCount = 0
  /\ lastAction = "init"
  /\ lastTimerSet = {}
  /\ lastTimerCleared = {}
  /\ lastSendBatch = <<>>
  /\ lastTouchedSub = None
  /\ lastTouchedSeq = NoSeq
  /\ lastTeardownSocketId = NoSocket

FlushLivenessInit ==
  /\ runtime = FlushLivenessRuntime
  /\ prevRuntime = FlushLivenessRuntime
  /\ stepCount = 0
  /\ lastAction = "init"
  /\ lastTimerSet = {}
  /\ lastTimerCleared = {}
  /\ lastSendBatch = <<>>
  /\ lastTouchedSub = None
  /\ lastTouchedSeq = NoSeq
  /\ lastTeardownSocketId = NoSocket

LivenessInit ==
  \/ ConnectLivenessInit
  \/ FlushLivenessInit

LivenessNext ==
  \/ RetryTimerFired
  \/ MarketFlushTimerFired

LivenessSpec ==
  /\ LivenessInit
  /\ [][LivenessNext]_vars
  /\ WF_vars(RetryTimerFired)
  /\ WF_vars(MarketFlushTimerFired)

ConnectReady ==
  /\ runtime.wsConfigured
  /\ runtime.online
  /\ ~runtime.intentionalClose
  /\ runtime.activeSocketId = NoSocket
  /\ runtime.retryTimerActive

ConnectInFlight ==
  /\ runtime.activeSocketId # NoSocket
  /\ runtime.status \in {"connecting", "reconnecting", "connected"}

FlushReady ==
  /\ runtime.marketFlushActive
  /\ HasPending(runtime.marketPending)

FlushCleared ==
  /\ ~runtime.marketFlushActive
  /\ ~HasPending(runtime.marketPending)

ConnectEventually ==
  ConnectReady ~> ConnectInFlight

MarketFlushEventually ==
  FlushReady ~> FlushCleared

LivenessView ==
  [status |-> runtime.status,
   socketId |-> runtime.socketId,
   activeSocketId |-> runtime.activeSocketId,
   online |-> runtime.online,
   hidden |-> runtime.hidden,
   intentionalClose |-> runtime.intentionalClose,
   retryTimerActive |-> runtime.retryTimerActive,
   marketFlushActive |-> runtime.marketFlushActive,
   marketPending |-> runtime.marketPending,
   wsConfigured |-> runtime.wsConfigured,
   stepCount |-> stepCount]

StateConstraint ==
  /\ runtime.socketId <= MaxSocketId
  /\ Cardinality(runtime.desiredSubscriptions) <= 2
  /\ PendingCount(runtime.marketPending) <= 1
  /\ runtime.attempt <= 1
  /\ stepCount <= MaxSteps

TypeOk ==
  /\ TypeRuntime(runtime)
  /\ TypeRuntime(prevRuntime)
  /\ stepCount \in 0..MaxSteps
  /\ lastAction \in
       {"init",
        "init_connection",
        "disconnect",
        "force_reconnect",
        "send_subscribe",
        "send_unsubscribe",
        "send_raw",
        "socket_open_active",
        "socket_close_active",
        "stale_socket_open",
        "stale_socket_close",
        "lifecycle_online",
        "lifecycle_offline",
        "lifecycle_visible",
        "lifecycle_hidden",
        "retry_timer_fired",
        "decoded_market",
        "decoded_lossless",
        "stale_decoded",
        "timer_market_flush",
        "parse_error_active",
        "stale_parse_error"}
  /\ lastTimerSet \subseteq TimerKeys
  /\ lastTimerCleared \subseteq TimerKeys
  /\ \A i \in 1..Len(lastSendBatch) : IsOutbound(lastSendBatch[i])
  /\ IF lastTouchedSub = None THEN TRUE ELSE lastTouchedSub \in SUBS
  /\ lastTouchedSeq \in 0..MaxSeq
  /\ lastTeardownSocketId \in 0..MaxSocketId

QueueBounded ==
  Len(runtime.queue) <= MaxQueueSize

ActiveSocketAuthority ==
  (lastAction \in StaleActions) => runtime = prevRuntime

RetryTimerCoherence ==
  (lastAction \in {"retry_timer_fired",
                   "disconnect",
                   "force_reconnect",
                   "socket_open_active",
                   "lifecycle_offline",
                   "socket_close_active"})
    => runtime.retryTimerActive = (runtime.nextRetryAt # None)

HealthTickUniqueness ==
  (lastAction \in {"init_connection", "force_reconnect"} /\ prevRuntime.healthTickActive)
    => ~("health-tick" \in lastTimerSet)

WatchdogUniqueness ==
  (lastAction = "init_connection" /\ prevRuntime.watchdogActive)
    => ~("watchdog" \in lastTimerSet)

MarketFlushUniqueness ==
  (lastAction = "decoded_market" /\ prevRuntime.marketFlushActive)
    => ~("market-flush" \in lastTimerSet)

MarketFlushClearsPending ==
  (lastAction = "timer_market_flush")
    => /\ ~HasPending(runtime.marketPending)
       /\ ~runtime.marketFlushActive

SubscriptionReplayOrder ==
  (lastAction = "socket_open_active")
    => /\ lastSendBatch = DesiredReplay(prevRuntime.desiredSubscriptions) \o prevRuntime.queue
       /\ runtime.queue = <<>>

SeqGapCounterSound ==
  (lastAction = "decoded_market"
    /\ lastTouchedSub # None
    /\ prevRuntime.streams[lastTouchedSub].subscribed
    /\ prevRuntime.streams[lastTouchedSub].lastSeq # NoSeq)
    =>
      runtime.streams[lastTouchedSub].seqGapCount =
        IF lastTouchedSeq > prevRuntime.streams[lastTouchedSub].lastSeq + 1
          THEN Bump(prevRuntime.streams[lastTouchedSub].seqGapCount)
          ELSE prevRuntime.streams[lastTouchedSub].seqGapCount

SubscribeClearsSeqGap ==
  (lastAction = "send_subscribe" /\ lastTouchedSub # None)
    => /\ runtime.streams[lastTouchedSub].lastSeq = NoSeq
       /\ ~runtime.streams[lastTouchedSub].seqGapDetected
       /\ runtime.streams[lastTouchedSub].seqGapCount = 0
       /\ runtime.streams[lastTouchedSub].lastGap = EmptyGap

DisconnectClearsVolatileState ==
  (lastAction \in {"disconnect", "lifecycle_offline", "force_reconnect"})
    => /\ ~runtime.retryTimerActive
       /\ runtime.nextRetryAt = None
       /\ ~runtime.marketFlushActive
       /\ ~HasPending(runtime.marketPending)

TeardownTracksPriorSocket ==
  (lastAction \in {"disconnect", "lifecycle_offline", "force_reconnect"} /\ prevRuntime.activeSocketId # NoSocket)
    => lastTeardownSocketId = prevRuntime.activeSocketId

IntentionalCloseSuppressesRetry ==
  (lastAction = "socket_close_active" /\ prevRuntime.intentionalClose)
    => /\ runtime.status = "disconnected"
       /\ ~runtime.retryTimerActive
       /\ runtime.nextRetryAt = None

=============================================================================
