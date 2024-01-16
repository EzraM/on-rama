(ns on-rama.sets
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rtest]
            [com.rpl.rama.path :as path]
            [clojure.test :as test]))

;; Sets with Rama turned out to be tricky, so this goes through making a PState to hold a set,
;; adding a value to the set, and writing query to check that a given value is in a set.

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

    ;; Checking if a value is in a set:
    (rama/<<query-topology topologies "kid-used-swing?"
                           ;; queries take inputs and emit results.
                           [*kid *swing :> *used-swing?]
                           ;; this data is partitioned by the swing, so we go to the partition
                           ;; of the swing we are interested in.
                           (rama/|hash *swing)
                           ;; this is an anonymous function that will be called as part of the path.
                           ;; the argument to the function is the set of kids who used the swing, pulled from the pstate.
                           ;; we use the baked-in contains? function from Clojure to check if the kid we're interested
                           ;; in is in this set, and emit the answer.
                           (rama/<<ramafn %had-kid [*kids]
                                          (:> (contains? *kids *kid)))
                           ;; this path resolves with a view that a emits of a boolean of whether or not a kid used the swing.
                           (rama/local-select> [(path/keypath *swing) (path/view %had-kid)] $$swing-sessions :> *used-swing?)
                           ;; all queries must, as their last operation, relocate computation to the partition where the query started.
                           (rama/|origin))

    (rama/<<sources s
                    (rama/source> *sessions :> {:keys [*kid *swing]})
                    (rama/|hash *swing)
                    (rama/local-transform> [(path/keypath *swing) path/NIL->SET path/NONE-ELEM (path/termval *kid)] $$swing-sessions))))

(test/deftest test-swingset []
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc SwingsetsModule {:tasks 4 :threads 2})
    (let [module-name (rama/get-module-name SwingsetsModule)
          sessions-depot (rama/foreign-depot ipc module-name "*sessions")
          used-swing? (rama/foreign-query ipc module-name "kid-used-swing?")]

      (rama/foreign-append! sessions-depot (->Session 1 10))
      (rama/foreign-append! sessions-depot (->Session 1 11))
      (rama/foreign-append! sessions-depot (->Session 2 10))

      (test/is (= true (rama/foreign-invoke-query used-swing? 1 10)))
      (test/is (= false (rama/foreign-invoke-query used-swing? 1 12)))
      (test/is (= true (rama/foreign-invoke-query used-swing? 2 10)))
      (test/is (= false (rama/foreign-invoke-query used-swing? 2 13)))
      (test/is (= false (rama/foreign-invoke-query used-swing? 3 10))))))

(comment
  (test-swingset))

