(ns on-rama.write-event
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rtest]
            [clojure.test :as test]))

;; Definition of an event
(defrecord AddUser [user-id])

;; A module with a depot
(rama/defmodule WriteEventModule [setup topologies]
  (rama/declare-depot setup *users (rama/hash-by :user-id)))

;; Run a module, write an event
(with-open [ipc (rtest/create-ipc)]
  (rtest/launch-module! ipc WriteEventModule {:tasks 4 :threads 2})
  (let [module-name (rama/get-module-name WriteEventModule)
        log (rama/foreign-depot ipc module-name "*users")]
    (rama/foreign-append! log (->AddUser 1))
    (rama/foreign-append! log {:user-id 2 :status :active})
    (rama/foreign-append! log (->AddUser 3))))