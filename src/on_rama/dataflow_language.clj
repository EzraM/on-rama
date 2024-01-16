(ns on-rama.dataflow-language
  (:require [com.rpl.rama :as rama]
            [com.rpl.rama.ops :as ops]
            [com.rpl.rama.test :as rtest]
            [clojure.test :as test]))


(rama/?<- (println "Hello!"))

(rama/?<-
 (inc 5 :> *six)
 (println *six))

(rama/?<-
 (ops/explode [{:user-id 1} {:user-id 2} {:user-id 3}] :> {:keys [*user-id]})
 (println *user-id))

(rama/?<-
 (ops/explode [{:user-id 1} {:user-id 2} {:user-id 3}] :> {:keys [*user-id]})
 (inc *user-id :> *inc-user-id)
 (println *inc-user-id))