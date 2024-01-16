(ns on-rama.simple-pstate
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.path :as path]
            [com.rpl.rama.test :as rtest]
            [clojure.test :as test]))

;; Definition of an event
(defrecord AddUser [user-id username])

;; A module with a depot
(rama/defmodule WritePStateModule [setup topologies]
  (rama/declare-depot setup *users (rama/hash-by :user-id))


  (let [s (rama/stream-topology)]
    (declare-pstate s $$usernames {Long ; user-id
                                   String ; username
                                   })

    (rama/<<sources s
                    (rama/source> *users :> {:keys [*user-id *username]})
                    (rama/local-transform> [(path/keypath *user-id) (path/termval *username)] $$usernames))))

;; Run a module, write an event, add to a PState, then read from the PState
(test/deftest []
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc WritePStateModule {:tasks 4 :threads 2})
    (let [module-name (rama/get-module-name WritePStateModule)
          log (rama/foreign-depot ipc module-name "*users")
          usernames (rama/foreign-pstate ipc module-name "$$usernames")]
      (rama/foreign-append! log (->AddUser 1 "Alex"))
      (rama/foreign-append! log (->AddUser 2 "Helene"))

      (= (rama/foreign-select-one (path/keypath 1) usernames) "Alex"))))