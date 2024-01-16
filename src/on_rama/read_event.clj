(ns on-rama.read-event
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.ops :as ops]
            [com.rpl.rama.test :as rtest]
            [clojure.test :as test]))

;; Definition of an event
(defrecord AddUser [user-id])

;; A module with a depot
(rama/defmodule ReadEventModule [setup topologies]
  (rama/declare-depot setup *users (rama/hash-by :user-id))

  (let [s (rama/stream-topology topologies "users")]
    (rama/<<sources s
                    (rama/source> *users :> {:keys [*user-id]})
                    (println (format "User id %s" *user-id)))))

;; Run a module, read an event
(with-open [ipc (rtest/create-ipc)]
  (rtest/launch-module! ipc ReadEventModule {:tasks 4 :threads 2})
  (let [module-name (rama/get-module-name ReadEventModule)
        log (rama/foreign-depot ipc module-name "*users")]
    (rama/foreign-append! log (->AddUser 1))
    (rama/foreign-append! log (->AddUser 2))))
