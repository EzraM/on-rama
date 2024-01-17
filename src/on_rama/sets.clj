(ns on-rama.sets
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rtest]
            [com.rpl.rama.path :as path]
            [clojure.test :as test]))

;; Sets with Rama turned out to be tricky, so this goes through making a PState to hold a set,
;; adding a value to the set, and running a contains? check from a view function to check if
;; a value is in the set

;; Our events will be kids taking turns on a swing-set. Get it?
(defrecord Session [kid swing])

(rama/defmodule SwingsetsModule [setup topologies]
  (rama/declare-depot setup *sessions (rama/hash-by :kid))

  (let [s (rama/stream-topology topologies "sets")]
    ;; PState is a map of swings to kids who have used the swing.
    ;; Rama does not support top-level sets, they have to be nested.
    (rama/declare-pstate s $$swing-sessions {Long ; swing
                                             (rama/set-schema Long ; kid
                                                              {:subindex? true})})
    (rama/<<sources s
                    (rama/source> *sessions :> {:keys [*kid *swing]})
                    (rama/|hash *swing)
                    (rama/local-transform> [(path/keypath *swing) path/NIL->SET path/NONE-ELEM (path/termval *kid)] $$swing-sessions)))

  ;; Usually, path/view cannot be called with code defined outside the module.
  ;; This is by design, for security.
  ;; With this utility, we relax that a bit, and make it possible to write a one-line `contains?` check.
  ;; `view-with-arg` is defined in the module and so can be called by path/view. This wrapper will allow
  ;; us to use a function and argument defined elsewhere to check the value from a pstate.
  (defn view-with-arg [view-fn arg]
    (fn [v] (view-fn v arg))))

(test/deftest test-swingset []
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc SwingsetsModule {:tasks 4 :threads 2})
    (let [module-name (rama/get-module-name SwingsetsModule)
          sessions-depot (rama/foreign-depot ipc module-name "*sessions")

          swing-sessions (rama/foreign-pstate ipc module-name "$$swing-sessions")]

      (rama/foreign-append! sessions-depot (->Session 1 10))
      (rama/foreign-append! sessions-depot (->Session 1 11))
      (rama/foreign-append! sessions-depot (->Session 2 10))

      ;; one-line contains check for a value in a set
      (test/is (= true (rama/foreign-select-one [(path/keypath 10) (path/view (view-with-arg contains? 1))] swing-sessions)))
      (test/is (= false (rama/foreign-select-one [(path/keypath 12) (path/view (view-with-arg contains? 1))] swing-sessions)))
      (test/is (= true (rama/foreign-select-one [(path/keypath 10) (path/view (view-with-arg contains? 2))] swing-sessions)))
      (test/is (= false (rama/foreign-select-one [(path/keypath 13) (path/view (view-with-arg contains? 2))] swing-sessions)))
      (test/is (= false (rama/foreign-select-one [(path/keypath 10) (path/view (view-with-arg contains? 3))] swing-sessions))))))

(comment
  (test-swingset))

