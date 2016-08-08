(ns kixi.heimdall.user
  (:require [buddy.hashers :as hs]))

#_(defn add-user! [ds user]
    (store/add-user! ds (update-in user [:password] #(hs/encrypt %))))
